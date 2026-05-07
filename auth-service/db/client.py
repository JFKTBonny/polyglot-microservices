import asyncpg
import os
from dotenv import load_dotenv

load_dotenv()

# Global connection pool
_pool = None

async def get_pool():
    global _pool
    if _pool is None:
        _pool = await asyncpg.create_pool(
            host=os.getenv("DB_HOST", "localhost"),
            port=int(os.getenv("DB_PORT", 5432)),
            user=os.getenv("DB_USER", "appuser"),
            password=os.getenv("DB_PASSWORD", "password"),
            database=os.getenv("DB_NAME", "authdb"),
            min_size=2,
            max_size=10,
        )
        print("[db] connection pool ready")
    return _pool

async def fetch_one(query: str, *args):
    pool = await get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(query, *args)
        return dict(row) if row else None

async def fetch_all(query: str, *args):
    pool = await get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(query, *args)
        return [dict(row) for row in rows]

async def execute(query: str, *args):
    pool = await get_pool()
    async with pool.acquire() as conn:
        return await conn.execute(query, *args)

async def fetch_val(query: str, *args):
    pool = await get_pool()
    async with pool.acquire() as conn:
        return await conn.fetchval(query, *args)

async def close_pool():
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None
        print("[db] connection pool closed")