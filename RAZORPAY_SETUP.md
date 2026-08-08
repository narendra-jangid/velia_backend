# Razorpay Integration — Setup, Testing & Deployment

## ⚠️ Important: you gave me a LIVE key, not a test key
`rzp_live_Sa7QpeyPzl...` is a **live** Razorpay key — it moves real money.
Razorpay's test card numbers (below) only work against **test** keys
(`rzp_test_...`), never live ones. If you try to "test" with a live key using
a fake card number, it will simply fail (as it should — that's Razorpay
protecting you).

**Recommended order of operations:**
1. Go to the Razorpay Dashboard → toggle to **Test Mode** → Settings → API Keys → generate a **test** key pair.
2. Run through the whole flow below using the test key + test cards until you're confident it works.
3. Only then switch `.env` to your **live** keys and go live.

**Also — please rotate the live secret you shared.** It was pasted in plaintext
in our chat, which means it's sitting in this conversation's history. Anyone
with `RAZORPAY_KEY_SECRET` can create/manipulate orders on your account, so
please regenerate it from the Razorpay Dashboard → Settings → API Keys once
you're done here, and use the new one.

---

## 1. What changed

**Root cause fixed:** `order-service`'s `OrderService.placeOrder()` was
completely stubbed out — no validation, no stock reduction, no pricing. It's
been restored and extended with Razorpay.

| File | What |
|---|---|
| `order-service/.../model/Order.java` | Added `razorpayOrderId/PaymentId/Signature` fields |
| `order-service/.../service/RazorpayService.java` | **New.** Creates Razorpay orders (REST), verifies payment + webhook signatures (HMAC-SHA256) |
| `order-service/.../service/OrderService.java` | Restored real `placeOrder()`; added `verifyPayment()`, `markPaymentFailed()`, `handleWebhookEvent()` |
| `order-service/.../controller/OrderController.java` | `POST /api/orders` now returns `razorpayOrder`; added `/razorpay/verify`, `/razorpay/payment-failed`, `/razorpay/webhook` |
| `order-service/.../config/RestTemplateConfig.java` | **New.** RestTemplate bean |
| `order-service/.../exception/PaymentVerificationException.java` | **New.** → 400 response |
| `order-service/src/main/resources/application.yml` | Added `razorpay.key-id/key-secret/webhook-secret` env vars |
| Frontend `src/app/api/orders/place/route.ts`, `verify-payment/route.ts` | Now thin proxies to the backend — no secret, no local Razorpay REST calls |
| Frontend `.env.local` | Only `NEXT_PUBLIC_RAZORPAY_KEY_ID` remains (public, safe to expose) |
| Frontend `src/app/Checkout/page.jsx` | Retry-safe: a failed/abandoned payment reopens the *same* Razorpay order instead of creating a duplicate |

No database schema migration is needed — MongoDB is schema-less; the new
`Order` fields simply appear as `null` on existing documents until set.

---

## 2. Environment variables

### Backend — `order-service` (set as real env vars, NOT committed to git)
```bash
RAZORPAY_KEY_ID=rzp_live_Sa7QpeyPzl...        # or rzp_test_... while testing
RAZORPAY_KEY_SECRET=ujKa60XF8HhNH9ki...       # never leaves the backend
RAZORPAY_WEBHOOK_SECRET=<set this in step 4>  # separate from the key secret
```

How to set them depends on how you run the service:
- **Local (terminal):** `export RAZORPAY_KEY_ID=... && export RAZORPAY_KEY_SECRET=... && mvn spring-boot:run`
- **IntelliJ:** Run Configuration → Environment Variables
- **Render / Railway / EC2 / etc:** set them in the host's environment variables dashboard — never in a committed `application.yml`

### Frontend — `.env.local` (already set)
```bash
NEXT_PUBLIC_RAZORPAY_KEY_ID=rzp_live_Sa7QpeyPzl...
```
This is the **public** key id — safe in client-side JS. The secret is never
here.

---

## 3. Installation

```bash
# Backend — each service, or use your existing multi-module build
cd order-service
mvn clean install

# Start in dependency order: eureka-server → api-gateway → product-service, order-service, user-service
```

```bash
# Frontend
cd veliafront
npm install
npm run dev
```

---

## 4. Setting up the webhook (recommended, not just nice-to-have)

The webhook is what makes payment status reliable even if the customer
closes the browser tab right after paying (client-side verify never fires,
but the webhook still will).

1. Razorpay Dashboard → **Settings → Webhooks → Add New Webhook**
2. URL: `https://<your-domain>/api/orders/razorpay/webhook` (through your API Gateway, publicly reachable)
3. Active events: check **`payment.captured`** and **`payment.failed`**
4. Set a webhook secret (any strong random string) — put the same value in `RAZORPAY_WEBHOOK_SECRET` on the backend
5. Save. Razorpay will show delivery attempts/responses in the dashboard for debugging.

While developing locally, use the Razorpay CLI or a tunnel (e.g. `ngrok http 8080`) to get a public URL for your gateway, and point the webhook at that temporarily.

---

## 5. Testing steps (use TEST mode keys for all of this)

1. Add products to cart → Checkout → fill delivery details → select **Razorpay**.
2. Click Pay. The Razorpay checkout widget should open (amount matches your cart total).
3. Use a Razorpay **test card**:

   | Field | Value |
   |---|---|
   | Card number | `4111 1111 1111 1111` (Visa) |
   | Expiry | any future date, e.g. `12/29` |
   | CVV | any 3 digits, e.g. `123` |
   | Name | any |
   | OTP (if prompted) | `1234` |

   Other test instruments:
   - UPI success: `success@razorpay`
   - UPI failure: `failure@razorpay`
   - Test card that **always fails**: `4000 0000 0000 0002`

4. On success: you should land on the success page, and the order in MongoDB should show `paymentStatus: "paid"` with `razorpayPaymentId` populated.
5. On failure (`4000 0000 0000 0002`): the Checkout page should show "Payment failed. You can retry the same order below." — click **Retry Payment** and confirm it reopens the *same* `razorpayOrderId` (check the Network tab — no new `POST /api/orders` call, no second stock reduction).
6. Close the widget without paying (click the X) → confirm the button still says **Pay** and lets you retry.
7. Check the webhook: in the Razorpay Dashboard → Webhooks → your endpoint, confirm you see `200` responses logged for the test payments above.
8. Duplicate-safety check: call `POST /api/orders/razorpay/verify` twice in a row with the same payload (e.g. via curl/Postman) — the second call should return success without changing anything (idempotent), not throw or double-process.

---

## 6. Production deployment checklist

- [ ] Switch `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` to **live** values (rotate the one shared in chat first)
- [ ] Set `RAZORPAY_WEBHOOK_SECRET` to a fresh value and re-register the **live** webhook URL in the Razorpay Dashboard
- [ ] Confirm the webhook endpoint is reachable over HTTPS from the public internet (not just your local machine)
- [ ] Double-check `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` are set only as environment variables on the host — never committed to git, never in `application.yml` defaults
- [ ] Complete Razorpay's KYC/activation (live mode is disabled until your account is activated)
- [ ] Enable HTTPS everywhere (API Gateway, order-service, frontend) — Razorpay requires HTTPS for live webhooks
- [ ] Add authentication/authorization to order-service endpoints — **currently there is none** (this backend has no Spring Security dependency at all). At minimum, protect the admin status/cancel endpoints before going live; the codebase doesn't have this infrastructure yet and building it is a separate, larger task.
- [ ] Consider rate-limiting `/api/orders` and `/razorpay/verify` at the gateway to slow down abuse/spam order creation
- [ ] Set up monitoring/alerts on order-service logs for `Razorpay order creation failed` and `Payment signature verification FAILED` — both indicate something needs attention
- [ ] Load-test the checkout flow once before a real launch spike (flash sale, etc.)
- [ ] Known trade-off (inherited from the existing design, not new): stock is reduced at order-creation time, before payment completes, for both COD and Razorpay. If a Razorpay payment is abandoned, that stock stays reduced until someone manually cancels the order. Fine at small scale; revisit with a stock-hold/reservation system if order volume grows.
