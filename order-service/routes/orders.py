from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, field_validator
from typing import List
from uuid import UUID
import json



from db.client import fetch_all, fetch_one, execute
from clients.user_client import get_user
from events.producer import publish  


router = APIRouter()

# ── Request / Response Models ──────────────────────────────────────────────
# Pydantic models serve as both validation AND documentation
# FastAPI reads these to auto-generate the OpenAPI spec

class OrderItem(BaseModel):
    product_id: str
    name: str
    quantity: int
    price: float

    @field_validator("quantity")
    def quantity_must_be_positive(cls, v):
        if v <= 0:
            raise ValueError("quantity must be greater than 0")
        return v

    @field_validator("price")
    def price_must_be_positive(cls, v):
        if v <= 0:
            raise ValueError("price must be greater than 0")
        return v

class CreateOrderRequest(BaseModel):
    user_id: UUID
    items: List[OrderItem]

    @field_validator("items")
    def items_must_not_be_empty(cls, v):
        if len(v) == 0:
            raise ValueError("order must have at least one item")
        return v

class OrderResponse(BaseModel):
    id: str
    user_id: str
    status: str
    total: float
    items: list
    created_at: str

# ── Routes ─────────────────────────────────────────────────────────────────

# GET /orders — list all orders, optionally filter by user_id
@router.get("/", response_model=dict)
async def list_orders(user_id: str = None, status: str = None):
    query = "SELECT * FROM orders WHERE 1=1"
    args = []

    if user_id:
        query += " AND user_id = %s"
        args.append(user_id)

    if status:
        query += " AND status = %s"
        args.append(status)

    query += " ORDER BY created_at DESC"
    rows = await fetch_all(query, tuple(args))

    # Parse items JSON string back to list for each row
    orders = []
    for row in rows:
        order = dict(row)
        order["items"] = json.loads(order["items"]) if isinstance(order["items"], str) else order["items"]
        order["created_at"] = str(order["created_at"])
        order["total"] = float(order["total"])
        orders.append(order)

    return {"data": orders, "count": len(orders)}


# GET /orders/:id
@router.get("/{order_id}", response_model=dict)
async def get_order(order_id: str):
    row = await fetch_one(
        "SELECT * FROM orders WHERE id = %s",
        (order_id,)
    )
    if not row:
        raise HTTPException(status_code=404, detail="Order not found")

    order = dict(row)
    order["items"] = json.loads(order["items"]) if isinstance(order["items"], str) else order["items"]
    order["created_at"] = str(order["created_at"])
    order["total"] = float(order["total"])
    return order


# POST /orders — create a new order
@router.post("/", response_model=dict, status_code=201)
async def create_order(body: CreateOrderRequest):

    # ── Step 1: Verify user exists in User Service ──
    user = await get_user(str(body.user_id))
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")

    # ── Step 2: Calculate total from items ──
    total = sum(item.price * item.quantity for item in body.items)
    items_json = json.dumps([item.model_dump() for item in body.items])

    # ── Step 3: Insert order into our own database ──
    try:
        await execute(
            """
            INSERT INTO orders (id, user_id, status, total, items)
            VALUES (UUID(), %s, 'pending', %s, %s)
            """,
            (str(body.user_id), total, items_json)
        )

        # ── Step 4: Fetch the created order to return it ──
        row = await fetch_one(
            "SELECT * FROM orders WHERE user_id = %s ORDER BY created_at DESC LIMIT 1",
            (str(body.user_id),)
        )

        order = dict(row)
        order["items"] = json.loads(order["items"]) if isinstance(order["items"], str) else order["items"]
        order["created_at"] = str(order["created_at"])
        order["total"] = float(order["total"])

        # ── Step 5: Publish event to Kafka ──────── ← add this block
        await publish(
            topic="order.created",
            key=str(body.user_id),
            payload={
                "order_id":  order["id"],
                "user_id":   order["user_id"],
                "total":     order["total"],
                "items":     order["items"],
                "status":    order["status"],
                "created_at": order["created_at"]
            }
        )


        return order

    except Exception as err:
        raise HTTPException(status_code=503, detail=str(err))


# DELETE /orders/:id
@router.delete("/{order_id}", status_code=204)
async def delete_order(order_id: str):
    row = await fetch_one(
        "SELECT id FROM orders WHERE id = %s",
        (order_id,)
    )
    if not row:
        raise HTTPException(status_code=404, detail="Order not found")

    await execute("DELETE FROM orders WHERE id = %s", (order_id,))