import bcrypt

def hash_password(password: str) -> str:
    """
    Hash a plain password using bcrypt.
    Returns a hashed string.
    """
    password_bytes = password.encode('utf-8')
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(password_bytes, salt)
    return hashed.decode('utf-8')

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    Verify a plain password against a bcrypt hash.
    Returns True if they match, False otherwise.
    """
    password_bytes = plain_password.encode('utf-8')
    hashed_bytes   = hashed_password.encode('utf-8')
    return bcrypt.checkpw(password_bytes, hashed_bytes)