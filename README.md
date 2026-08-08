# Velia Microservices

Spring Boot microservices built from the **Velia e-commerce admin panel** (Next.js/MongoDB).
Reads and writes the same MongoDB Atlas cluster (`velia` database) as the original Next.js app.

---

## Architecture

```
CLIENT (Velia Next.js app)
  │
  ▼
┌──────────────────────────┐
│    API GATEWAY  :8080    │  Routes all traffic, circuit breakers
└────────────┬─────────────┘
             │  lb:// via Eureka
             ▼
┌────────────────────────────┐
│    EUREKA SERVER  :8761    │  Service registry
└──┬──────────┬──────────┬───┘
   │          │          │
   ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐
│PRODUCT │ │ ORDER  │ │  USER  │
│SERVICE │◄│SERVICE │ │SERVICE │
│ :8081  │ │ :8082  │ │ :8083  │
└───┬────┘ └───┬────┘ └───┬────┘
    │          │          │
    └────────┬─┴──────────┘
             ▼
   ┌──────────────────┐
   │   MongoDB Atlas   │  Shared "velia" database
   │   products        │  Same cluster as Next.js app
   │   orders          │
   │   users           │  cart embedded on user doc
   │   otp_records     │  temporary OTP storage
   └──────────────────┘
```

Order Service calls Product Service via Feign (`ProductClient`) to validate stock
and snapshot product details into each order — same pattern as before, just
extended to loop over every line item in a cart instead of a single product.

---

## Database Config

All three data services connect to the **same MongoDB Atlas cluster** via:

```
MONGODB_URI=mongodb+srv://veliajaipurofficial_db_user:ShVSWJws21kJi4ZY@cluster0.gygaeft.mongodb.net/velia?appName=Cluster0
```

- **product-service** → reads/writes `products` collection
- **order-service** → reads/writes `orders` collection
- **user-service** → reads/writes `users` collection

To override, set `MONGODB_URI` as an environment variable before starting.

---

## Cloudinary Config

Product Service uses the same credentials as the Velia Next.js app:

```
CLOUDINARY_CLOUD_NAME=dvzqub6y9
CLOUDINARY_API_KEY=373875499722699
CLOUDINARY_API_SECRET=Cfd8xiFjjtJ7NGBZ7EayOo6UDvw
```

Uploads default to the `velia/products` folder — pass a `folder` field on
`POST /api/products/upload` to organise thumbnails vs. gallery shots.

---

## How to Run

```bash
# 1. Build from root
mvn clean package -DskipTests

# 2. Start in order (each in its own terminal)
cd eureka-server   && mvn spring-boot:run   # :8761
cd product-service && mvn spring-boot:run   # :8081
cd order-service   && mvn spring-boot:run   # :8082
cd user-service    && mvn spring-boot:run   # :8083
cd api-gateway     && mvn spring-boot:run   # :8080
```

All external traffic should go through the gateway on **:8080** — that's the
`PRODUCT_SERVICE_URL` you point the Next.js app's `.env.local` at.

---

## API Reference (all via Gateway :8080)

### Products

| Method | Path                                 | Description                          |
|--------|---------------------------------------|--------------------------------------|
| GET    | `/api/products`                       | All products (sorted newest first)   |
| GET    | `/api/products/featured`              | featured: true only                  |
| GET    | `/api/products/category/{category}`   | Filter by category                   |
| GET    | `/api/products/slug/{slug}`           | Find by slug                         |
| GET    | `/api/products/{id}`                  | Find by MongoDB ObjectId             |
| POST   | `/api/products`                       | Create (JSON body) → returns Product |
| PUT    | `/api/products/{id}`                  | Update (JSON body) → returns Product |
| DELETE | `/api/products/{id}`                  | Delete                               |
| POST   | `/api/products/upload`                | Upload one image → `{ "url": "..." }` |
| POST   | `/api/products/{id}/reduce-stock`     | Internal — called by Order Service   |

**Create/Update JSON body** (matches the Velia Next.js admin form exactly):
```json
{
  "name": "...", "slug": "...", "category": "...", "brand": "...", "description": "...",
  "price": 1299, "mrp": 1999, "stock": 20,
  "fabric": "...", "fit": "...", "pattern": "...", "length": "...",
  "neckType": "...", "sleeve": "...", "occasion": "...",
  "washCare": "...", "whatsIncluded": "...", "weight": "...", "height": "...",
  "liningAvailable": false, "featured": false, "active": true,
  "sizes": [{ "size": "M", "dimension": "38" }],
  "colors": ["red", "blue"],
  "thumbnail": "https://res.cloudinary.com/...",
  "images": ["https://res.cloudinary.com/..."]
}
```

**Upload image** — `multipart/form-data`: `file` (the image), `folder` (optional, defaults to `velia/products`). Upload each image first, then send the returned URLs in the `thumbnail`/`images` fields above.

### Orders

| Method | Path                            | Description                                    |
|--------|----------------------------------|------------------------------------------------|
| GET    | `/api/orders`                    | All orders (newest first)                      |
| GET    | `/api/orders/{id}`                | Order by ID                                     |
| GET    | `/api/orders/by-phone/{phone}`    | Orders for a customer (Velia "My Orders" page)  |
| GET    | `/api/orders/by-email/{email}`    | Orders by customer email                        |
| POST   | `/api/orders`                     | Place a cart-checkout order                     |
| PATCH  | `/api/orders/{id}/status`         | Admin — update fulfilment status                |
| PATCH  | `/api/orders/{id}/cancel`         | Cancel an order                                 |

**Place Order body** (matches the Velia Next.js Checkout page payload):
```json
{
  "customer": {
    "name": "Narendra", "phone": "9876543210", "email": "narendra@example.com",
    "address": "123 MG Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"
  },
  "items": [
    { "id": "<product ObjectId>", "name": "Floral Wrap Dress", "img": "https://...",
      "price": 1299, "qty": 1, "size": "M", "color": "Rose" }
  ],
  "subtotal": 1299, "discount": 0, "delivery": 0, "total": 1299,
  "paymentMethod": "cod"
}
```

When an order is placed:
1. For each item: Feign calls Product Service to fetch product details
2. Validates `active: true` and sufficient `stock` for that item
3. Feign calls Product Service to reduce stock for that item
4. Snapshots `name`, `thumbnail`, `price` into the order line item
5. Generates a human-readable `orderId` (e.g. `VL-10023456`)
6. Saves the order to the `orders` collection with `status: "Confirmed"`

Only `paymentMethod: "cod"` is accepted right now — Razorpay/Card/UPI are
modelled (see `PaymentMethod.java`) but rejected server-side until real
gateway keys and KYC approval are in place.

**Update status body:** `{ "status": "Shipped" }` — one of `Pending`, `Confirmed`, `Shipped`, `Delivered`, `Cancelled`.

### Users

| Method | Path                              | Description                                         |
|--------|-----------------------------------|-----------------------------------------------------|
| POST   | `/api/users/otp/send`             | Generate & send OTP to phone (logged in dev mode)   |
| POST   | `/api/users/otp/verify`           | Verify OTP → create/login user                      |
| POST   | `/api/users/verify`               | Legacy upsert (no OTP check)                        |
| GET    | `/api/users/{phone}`              | Fetch profile by phone                              |
| PUT    | `/api/users/{phone}`              | Update profile / default shipping address           |
| GET    | `/api/users/{phone}/cart`         | Get persisted cart for user                         |
| POST   | `/api/users/{phone}/cart`         | Add item to cart                                    |
| PUT    | `/api/users/{phone}/cart/{id}`    | Update cart item (qty, size, color)                 |
| DELETE | `/api/users/{phone}/cart/{id}`    | Remove one cart item                                |
| DELETE | `/api/users/{phone}/cart`         | Clear entire cart                                   |

**Send OTP body:** `{ "phone": "9876543210" }` — in dev, response includes `"otp": "123456"`.

**Verify OTP body:** `{ "phone": "9876543210", "otp": "123456", "name": "Narendra" }` — `name` optional.

**Update body** (any subset): `{ "name", "email", "address", "city", "state", "pincode" }` — also accepts `zip` / `zipcode`.

**Add to cart body:**
```json
{ "productId": "<id>", "name": "...", "img": "https://...", "price": 1299, "qty": 1, "size": "M", "color": "Rose" }
```

---

## Microservices Concepts Covered

| Concept                   | Where                                                  |
|----------------------------|--------------------------------------------------------|
| Service Discovery          | Eureka + `@EnableDiscoveryClient`                      |
| API Gateway + Routing      | Spring Cloud Gateway, path-based                        |
| Load Balancing             | `lb://` prefix via Eureka                                |
| Feign Client                | Order → Product via `ProductClient`, called per cart item |
| Circuit Breaker             | Resilience4j fallback on Feign + Gateway filter          |
| Shared Database             | Single MongoDB Atlas "velia" DB, separate collections    |
| Document Auditing           | `@EnableMongoAuditing`, `@CreatedDate`                    |
| Cloudinary Upload           | `ProductService.uploadToCloudinary`, exposed via `/api/products/upload` |
| Global Exception Handler    | `@RestControllerAdvice` in every service                 |
