## RSA Key Files

This directory holds the RSA-2048 key pair used to sign and verify JWT tokens.

**Generate them before first run:**

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

**What goes here:**
- `public.pem`  — public key, **safe to commit**
- `private.pem` — private key, **git-ignored, never commit**

The `private.pem` rule is enforced in `.gitignore`.
