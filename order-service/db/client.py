import aiomysql
import os
from dotenv import load_dotenv

load_dotenv()

# Global connection pool — created once on startup, reused across all requests
_pool = None

async def get_pool():
    global _pool
    if _pool is None:
        _pool = await aiomysql.create_pool(
            host=os.getenv("DB_HOST", "localhost"),
            port=int(os.getenv("DB_PORT", 3306)),
            user=os.getenv("DB_USER", "appuser"),
            password=os.getenv("DB_PASSWORD", "password"),
            db=os.getenv("DB_NAME", "orderdb"),
            autocommit=True,
            minsize=2,
            maxsize=10,
            charset="utf8mb4"
        )
    return _pool

async def fetch_all(query: str, args: tuple = ()):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(query, args)
            return await cur.fetchall()

async def fetch_one(query: str, args: tuple = ()):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(query, args)
            return await cur.fetchone()

async def execute(query: str, args: tuple = ()):
    pool = await get_pool()
    async with pool.acquire() as conn:
        async with conn.cursor(aiomysql.DictCursor) as cur:
            await cur.execute(query, args)
            return cur.lastrowid

async def close_pool():
    global _pool
    if _pool is not None:
        _pool.close()
        await _pool.wait_closed()
        _pool = None