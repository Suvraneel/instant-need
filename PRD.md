# Product Requirements Document

## Product Name
Wholesale B2B Ordering Platform

## Version
v1.0

## Date
2026-05-23

## Document Owner
Product / Delivery Team

## 1. Executive Summary
The Wholesale B2B Ordering Platform is a multi-channel ordering system for wholesale customers to browse a catalog, view quantity-based pricing, create carts, and place orders across Android, iOS, and web. All channels will use a shared backend, database, pricing engine, order management layer, and admin CMS.

The platform is intended to replace manual order intake via phone, chat, spreadsheets, or email with a structured digital workflow. The first release targets approximately 500 active wholesale customers and thousands of products, while providing a foundation for future scale, deeper automation, and optional payment integrations later.

## 2. Product Vision
Create the simplest and most reliable way for wholesale customers to place repeat and bulk orders digitally, while enabling internal teams to manage products, pricing, customers, and orders without engineering support.

## 3. Goals
- Enable wholesale customers to place orders in under 3 minutes for repeat purchasing scenarios.
- Support Android, iOS, and responsive web with consistent business logic and experience.
- Allow admins to manage catalog, pricing tiers, customers, and orders through a CMS.
- Reduce manual order entry effort and order errors.
- Support initial scale of 500+ active customers and future growth.

## 4. Non-Goals
- Online payment gateway integration in v1.
- Marketplace functionality with multiple vendors.
- Advanced procurement workflows such as RFQ, credit approvals, or negotiated quote flows.
- Warehouse management, route optimization, or ERP replacement.
- Complex promotions, loyalty, or subscription ordering in v1.

## 5. Product Scope

### In Scope
- Customer registration, login, password reset, profile management
- Product catalog browsing, search, filtering, product detail views
- Quantity-based pricing tiers with real-time pricing calculation
- Shopping cart and checkout
- COD / offline payment messaging
- Order placement and order status tracking
- Customer dashboard and order history
- Admin CMS for products, categories, inventory status, customers, orders, and reporting
- Email notifications; SMS optional if provider is enabled

### Out of Scope
- Online payments
- Dynamic credit limits
- Distributor commission management
- Real-time logistics integration
- Returns and refunds workflow automation

## 6. User Personas

### 6.1 Wholesale Buyer
- Small or medium business purchaser
- Places recurring bulk orders
- Needs fast ordering, clear pricing tiers, and reorder capability
- Often mobile-first, but may also order from desktop during work hours

### 6.2 Sales / Operations Admin
- Internal business staff managing products, orders, and customers
- Needs simple CMS workflows without developer dependency
- Requires order visibility, customer lookup, pricing control, and exports

### 6.3 Business Owner / Manager
- Reviews sales performance and operational efficiency
- Needs reports on orders, top products, and customer activity

## 7. Key User Problems
- Buyers do not have a single trusted place to see current wholesale pricing.
- Manual ordering causes delays, missing details, and pricing mistakes.
- Buyers need visibility into availability, order status, and repeat purchases.
- Admin teams need non-technical control over products, pricing, and orders.

## 8. Success Metrics
- 80% of repeat customers can place a reorder within 3 minutes.
- 95% of submitted orders include complete shipping and contact information.
- 90% of product and pricing changes are handled by admins without developer support.
- 30% reduction in manual order processing time within 3 months of launch.
- Platform supports 500 active wholesale customers and thousands of products with no major degradation.
- Web catalog search responses under 1 second for common queries at expected load.

## 9. Assumptions and Constraints
- Customers are businesses, not retail consumers.
- No online payment collection is needed in v1.
- COD and offline payment arrangements are sufficient for initial launch.
- Product prices may vary only by quantity tier in v1, not by individual contract.
- Initial launch supports one business entity and one product catalog.
- Admin team can maintain catalog data if CMS usability is strong.
- Mobile and web should share core logic where feasible to reduce maintenance cost.

## 10. Functional Requirements

### 10.1 Cross-Platform Experience
- FR-1: Android, iOS, and web must expose the same core business features.
- FR-2: Shared backend must be the source of truth for users, products, prices, carts, and orders.
- FR-3: Authentication, pricing, catalog, and order rules must behave consistently across channels.
- FR-4: The web app must be responsive for desktop, tablet, and mobile browsers.

### 10.2 Authentication and Account Management
- FR-5: Users must register before placing an order.
- FR-6: Registration must capture full name, email, phone number, shipping address, city, state, country, postal code, with optional business name, GST/VAT number, and notes.
- FR-7: Users must log in using email and password.
- FR-8: The system may optionally support phone number and OTP login.
- FR-9: Users must be able to reset password via email.
- FR-10: Users must be able to edit profile and address details.
- FR-11: Admins must be able to activate and deactivate customer accounts.

### 10.3 Product Catalog
- FR-12: Customers must browse products by category.
- FR-13: Customers must search by product name and SKU.
- FR-14: Customers must filter by category and availability.
- FR-15: Product detail pages must show name, images, description, SKU, category, unit of measurement, availability, and quantity-based pricing tiers.
- FR-16: The system must support thousands of products.

### 10.4 Quantity-Based Pricing
- FR-17: Each product must support one or more quantity pricing tiers.
- FR-18: The applicable unit price must update automatically based on selected quantity.
- FR-19: Product pages and cart must display quantity, unit price, line total, and tier table.
- FR-20: Pricing calculation must be server-authoritative and reproducible for orders.
- FR-21: Pricing tiers must be manageable by admins through CMS.

### 10.5 Cart
- FR-22: Customers must add products to cart from catalog and product detail pages.
- FR-23: Customers must update item quantities and remove items from cart.
- FR-24: Cart must persist for the active signed-in session and ideally across devices for the same account.
- FR-25: Cart must show image, name, quantity, unit price, line total, and grand total.
- FR-26: Cart recalculation must occur in real time when quantity changes.

### 10.6 Checkout and Order Placement
- FR-27: Customers must review cart before placing order.
- FR-28: Customers must confirm or edit shipping information at checkout.
- FR-29: Checkout must clearly display the message: "Payment will be collected separately after order confirmation."
- FR-30: Customers must submit orders without online payment.
- FR-31: The system must generate a unique order number.
- FR-32: Order confirmation screen must display order number, summary, and next steps.
- FR-33: Order confirmation must be sent by email; SMS is optional if enabled.

### 10.7 Order Management
- FR-34: Orders must store customer snapshot, address snapshot, line items, unit prices, and totals at submission time.
- FR-35: Order statuses must include Pending, Confirmed, Processing, Shipped, Delivered, Cancelled.
- FR-36: Customers must view order history and order details.
- FR-37: Customers must reorder from a previous order.
- FR-38: Admins must search and filter orders by status, customer, date, and order number.
- FR-39: Admins must update order status in CMS.

### 10.8 Notifications
- FR-40: Customers must receive order confirmation notifications.
- FR-41: Customers should receive order status update notifications.
- FR-42: Admins should receive new order alerts.
- FR-43: Admins should receive customer registration alerts.

### 10.9 Reporting and Export
- FR-44: Admins must view sales reports by date range.
- FR-45: Admins must view top-selling products.
- FR-46: Admins must view customer activity summaries.
- FR-47: Admins must export order data to CSV.
- FR-48: Admins must export order data to Excel.

## 11. Non-Functional Requirements
- NFR-1: Mobile-first UX across all channels.
- NFR-2: 95th percentile API response for standard read operations under 800 ms at expected launch load.
- NFR-3: Search results should return under 1 second for common queries.
- NFR-4: Web app should meet Core Web Vitals targets appropriate for catalog pages.
- NFR-5: Role-based authorization for admin and customer access.
- NFR-6: Passwords must be hashed using modern algorithms such as Argon2 or bcrypt.
- NFR-7: Sensitive data must be encrypted in transit via TLS and encrypted at rest where supported.
- NFR-8: System must support backups, point-in-time recovery, and restore drills.
- NFR-9: Architecture must scale horizontally for app and API tiers.
- NFR-10: Availability target should be 99.5% or higher for production.
- NFR-11: Audit logs should exist for admin changes to products, pricing, customers, and orders.
- NFR-12: Web app must be SEO-friendly for public catalog pages if catalog visibility is intended to be public; if catalog is private, SEO scope is limited to marketing pages only.

## 12. Detailed User Stories

### 12.1 Customer User Stories
- As a wholesale buyer, I want to register with my business and shipping details so I can place orders quickly.
- As a wholesale buyer, I want to log in securely so I can access my account and orders.
- As a wholesale buyer, I want to browse categories so I can find products efficiently.
- As a wholesale buyer, I want to search by product name or SKU so I can locate exact products quickly.
- As a wholesale buyer, I want to see pricing tiers before adding a product so I understand quantity discounts.
- As a wholesale buyer, I want the unit price to update when I change quantity so I know the exact price I will pay.
- As a wholesale buyer, I want to update quantities in the cart so I can optimize my order value.
- As a wholesale buyer, I want to confirm my shipping details before submitting so orders go to the correct location.
- As a wholesale buyer, I want to receive an order number after placing an order so I can reference it later.
- As a wholesale buyer, I want to view order history and reorder past items so repeat purchasing is fast.
- As a wholesale buyer, I want to track order status so I know whether my order is pending, confirmed, or delivered.

### 12.2 Admin User Stories
- As an admin, I want to create and edit products so the catalog remains current.
- As an admin, I want to manage categories and availability so customers only see valid offerings.
- As an admin, I want to configure pricing tiers so wholesale discounts are accurate.
- As an admin, I want to view and update customer accounts so I can control access and correct data.
- As an admin, I want to receive alerts for new registrations and new orders so I can respond quickly.
- As an admin, I want to filter orders by status and date so I can manage fulfillment efficiently.
- As an admin, I want to export orders to CSV or Excel so I can share and analyze data offline.
- As a manager, I want to see top products and sales trends so I can make inventory and sales decisions.

## 13. Wireframe Descriptions

### 13.1 Home Page
- Top bar with logo, search, login/account, and cart icon
- Primary category navigation
- Hero area with featured categories or promotions
- Product blocks for popular or recently ordered items
- Footer with contact information and policies

### 13.2 Product Catalog Page
- Search bar at top
- Left or collapsible filters for category and availability
- Product grid or list with image, name, SKU, unit, starting price, availability
- Quick quantity selector and add-to-cart action where practical
- Pagination or infinite scroll

### 13.3 Product Details Page
- Large product image gallery
- Product name, SKU, category, unit of measurement, availability badge
- Pricing tier table
- Quantity selector
- Dynamic unit price and line total
- Add to cart button
- Description and additional product details

### 13.4 Shopping Cart Page
- List of cart items with image, title, SKU, quantity controls, unit price, line total
- Remove item action
- Cart subtotal / grand total summary
- Continue shopping and proceed to checkout actions

### 13.5 Login Page
- Email and password inputs
- Forgot password link
- Optional phone/OTP alternative
- CTA to register

### 13.6 Registration Page
- Personal details section
- Business details section
- Shipping address section
- Optional GST/VAT and notes fields
- Password creation
- Submit and login redirect

### 13.7 Checkout Page
- Shipping information review/edit
- Order summary section
- COD / offline payment message
- Place order button

### 13.8 Customer Dashboard
- Profile summary card
- Quick links to orders, addresses, reorder, account settings
- Recent order list with statuses

### 13.9 Order History Page
- Search and filter by status/date
- Order list with order number, date, total, status
- Reorder and view details actions

### 13.10 Admin Dashboard
- KPI cards for total orders, pending orders, sales, active customers
- Navigation for products, categories, customers, orders, reports, exports, settings
- Recent orders table and alerts

## 14. Technical Architecture

### 14.1 Recommended Stack
- Mobile: React Native for Android and iOS with shared business logic and API layer
- Web: Next.js or React-based responsive web app
- Backend: Modular REST API with optional GraphQL read layer later
- Database: PostgreSQL
- Cache / queue: Redis
- File storage: S3-compatible object storage for product images
- CMS/Admin: Custom admin app built on the same backend, or a headless admin framework such as Strapi, Directus, or a custom Next.js admin panel
- Notifications: Email provider such as SES/SendGrid; SMS provider optional
- Hosting: Cloud deployment with containerized services

### 14.2 Architecture Principles
- Shared domain services for product, pricing, cart, customer, and order logic
- API-first design for all clients
- Stateless app servers for horizontal scaling
- Server-side validation and pricing calculations
- Separation between customer-facing app and admin capabilities via RBAC

### 14.3 Logical Components
- Client Apps
  - React Native mobile apps
  - Responsive web app
- API Layer
  - Auth service
  - Catalog service
  - Pricing service
  - Cart service
  - Order service
  - Customer profile service
  - Admin/reporting service
- Data Layer
  - PostgreSQL
  - Redis cache/session store
  - Object storage for images
- Integrations
  - Email service
  - Optional SMS service
  - Analytics / logging / monitoring

### 14.4 Scalability Considerations
- Use CDN for web assets and product images
- Cache category and product listing queries
- Index search fields such as name, SKU, category, and availability
- Queue non-blocking tasks such as email/SMS notifications and exports
- Design database with clear indexing and order snapshots to preserve history

## 15. CMS / Admin Architecture Recommendation
Recommended approach: custom admin web app backed by the same API and RBAC model, because pricing tiers, order workflows, exports, and audit logging are business-specific. A headless CMS can still be used for structured catalog content, but operational workflows such as order management are better handled in a dedicated admin portal.

### CMS Capabilities
- Product CRUD with image upload
- Category CRUD
- Pricing tier management by product
- Availability status management
- Customer search, edit, activate/deactivate
- Order search, filter, detail view, status updates
- Reporting dashboards
- CSV/XLSX export jobs
- Admin roles and permissions
- Audit logs

## 16. Database Design

### 16.1 Core Entity Overview
- `users`: authentication identity
- `customers`: business/customer profile linked to user
- `addresses`: reusable shipping/billing addresses
- `categories`: product grouping
- `products`: sellable catalog items
- `product_images`: multiple images per product
- `pricing_tiers`: quantity-based pricing rules per product
- `carts`: active cart per customer
- `cart_items`: products and quantities in cart
- `orders`: order header snapshot
- `order_items`: order line item snapshot
- `notifications`: system notifications
- `admin_audit_logs`: admin activity tracking

### 16.2 Table Schema Summary

#### users
- id (PK, UUID)
- email (unique, nullable if OTP-only enabled)
- phone_number (unique, nullable)
- password_hash
- auth_provider
- is_active
- last_login_at
- created_at
- updated_at

#### customers
- id (PK, UUID)
- user_id (FK -> users.id, unique)
- full_name
- business_name (nullable)
- gst_vat_number (nullable)
- notes (nullable)
- default_shipping_address_id (FK -> addresses.id, nullable)
- created_at
- updated_at

#### addresses
- id (PK, UUID)
- customer_id (FK -> customers.id)
- label
- line1
- line2 (nullable)
- city
- state
- country
- postal_code
- is_default
- created_at
- updated_at

#### categories
- id (PK, UUID)
- parent_id (FK -> categories.id, nullable)
- name
- slug (unique)
- is_active
- sort_order
- created_at
- updated_at

#### products
- id (PK, UUID)
- category_id (FK -> categories.id)
- name
- slug (unique)
- sku (unique)
- description
- unit_of_measurement
- availability_status
- is_active
- base_price (optional reference price)
- created_at
- updated_at

#### product_images
- id (PK, UUID)
- product_id (FK -> products.id)
- image_url
- alt_text
- sort_order
- created_at

#### pricing_tiers
- id (PK, UUID)
- product_id (FK -> products.id)
- min_quantity
- max_quantity (nullable for open-ended tier)
- unit_price
- currency_code
- created_at
- updated_at

#### carts
- id (PK, UUID)
- customer_id (FK -> customers.id, unique for active cart)
- status
- created_at
- updated_at

#### cart_items
- id (PK, UUID)
- cart_id (FK -> carts.id)
- product_id (FK -> products.id)
- quantity
- applied_unit_price
- line_total
- created_at
- updated_at

#### orders
- id (PK, UUID)
- order_number (unique)
- customer_id (FK -> customers.id)
- shipping_address_snapshot_json
- customer_snapshot_json
- status
- payment_method
- payment_note
- subtotal_amount
- total_amount
- currency_code
- placed_at
- created_at
- updated_at

#### order_items
- id (PK, UUID)
- order_id (FK -> orders.id)
- product_id (FK -> products.id)
- product_name_snapshot
- sku_snapshot
- unit_of_measurement_snapshot
- quantity
- unit_price
- line_total
- pricing_tier_snapshot_json
- created_at

#### notifications
- id (PK, UUID)
- recipient_type
- recipient_id
- channel
- notification_type
- subject
- message
- status
- sent_at (nullable)
- created_at

#### admin_audit_logs
- id (PK, UUID)
- admin_user_id (FK -> users.id)
- action_type
- entity_type
- entity_id
- before_json
- after_json
- created_at

### 16.3 Relationships
- One `user` has one `customer`
- One `customer` has many `addresses`
- One `category` has many `products`
- One `product` has many `product_images`
- One `product` has many `pricing_tiers`
- One `customer` has one active `cart`
- One `cart` has many `cart_items`
- One `customer` has many `orders`
- One `order` has many `order_items`
- Notifications may target customers or admins

### 16.4 Indexing Recommendations
- `users.email`, `users.phone_number`
- `products.sku`, `products.slug`, `products.name`
- `products.category_id`, `products.availability_status`
- `pricing_tiers.product_id`, `pricing_tiers.min_quantity`
- `orders.order_number`, `orders.customer_id`, `orders.status`, `orders.placed_at`
- Full-text or trigram index for product search by name/SKU

## 17. API Specifications
Recommended style: versioned REST API, JSON over HTTPS, JWT or secure session token auth.

Base path: `/api/v1`

### 17.1 Authentication APIs

#### POST `/auth/register`
Request:
```json
{
  "full_name": "Rahul Mehta",
  "business_name": "Mehta Traders",
  "email": "rahul@example.com",
  "phone_number": "+919900000000",
  "password": "StrongPassword123",
  "gst_vat_number": "29ABCDE1234F2Z5",
  "address": {
    "line1": "12 Market Road",
    "city": "Bengaluru",
    "state": "Karnataka",
    "country": "India",
    "postal_code": "560001"
  }
}
```

Response:
```json
{
  "user_id": "usr_123",
  "customer_id": "cus_123",
  "message": "Registration successful"
}
```

#### POST `/auth/login`
```json
{
  "email": "rahul@example.com",
  "password": "StrongPassword123"
}
```

Response:
```json
{
  "access_token": "jwt_token",
  "refresh_token": "refresh_token",
  "user": {
    "id": "usr_123",
    "full_name": "Rahul Mehta"
  }
}
```

#### POST `/auth/forgot-password`
#### POST `/auth/reset-password`
#### POST `/auth/send-otp` (optional)
#### POST `/auth/verify-otp` (optional)

### 17.2 Product and Category APIs

#### GET `/categories`
Response:
```json
[
  {
    "id": "cat_1",
    "name": "Beverages",
    "slug": "beverages"
  }
]
```

#### GET `/products`
Supported query params:
- `search`
- `category_id`
- `availability`
- `page`
- `limit`
- `sort`

Response:
```json
{
  "items": [
    {
      "id": "prd_1",
      "name": "Premium Tea Box",
      "sku": "TEA-001",
      "unit_of_measurement": "box",
      "availability_status": "in_stock",
      "starting_price": 980,
      "currency_code": "INR",
      "image_url": "https://cdn.example.com/p1.jpg"
    }
  ],
  "page": 1,
  "limit": 20,
  "total": 2500
}
```

#### GET `/products/{id}`
Response:
```json
{
  "id": "prd_1",
  "name": "Premium Tea Box",
  "sku": "TEA-001",
  "description": "Bulk packed tea box",
  "unit_of_measurement": "box",
  "availability_status": "in_stock",
  "pricing_tiers": [
    { "min_quantity": 1, "max_quantity": 9, "unit_price": 1000 },
    { "min_quantity": 10, "max_quantity": 19, "unit_price": 990 },
    { "min_quantity": 20, "max_quantity": null, "unit_price": 980 }
  ]
}
```

### 17.3 Pricing API

#### POST `/pricing/calculate`
```json
{
  "product_id": "prd_1",
  "quantity": 12
}
```

Response:
```json
{
  "product_id": "prd_1",
  "quantity": 12,
  "applied_unit_price": 990,
  "line_total": 11880,
  "currency_code": "INR",
  "matched_tier": {
    "min_quantity": 10,
    "max_quantity": 19
  }
}
```

### 17.4 Cart APIs

#### GET `/cart`
#### POST `/cart/items`
```json
{
  "product_id": "prd_1",
  "quantity": 12
}
```

#### PATCH `/cart/items/{item_id}`
```json
{
  "quantity": 20
}
```

#### DELETE `/cart/items/{item_id}`

Cart response example:
```json
{
  "cart_id": "cart_123",
  "items": [
    {
      "item_id": "ci_1",
      "product_id": "prd_1",
      "name": "Premium Tea Box",
      "quantity": 20,
      "unit_price": 980,
      "line_total": 19600
    }
  ],
  "grand_total": 19600,
  "currency_code": "INR"
}
```

### 17.5 Order APIs

#### POST `/orders`
```json
{
  "shipping_address_id": "addr_123",
  "payment_method": "cod",
  "customer_note": "Deliver during business hours"
}
```

Response:
```json
{
  "order_id": "ord_123",
  "order_number": "WB-20260523-0001",
  "status": "pending",
  "message": "Order placed successfully"
}
```

#### GET `/orders`
#### GET `/orders/{id}`
#### POST `/orders/{id}/reorder`

### 17.6 Customer Profile APIs
- `GET /me`
- `PATCH /me`
- `GET /me/addresses`
- `POST /me/addresses`
- `PATCH /me/addresses/{id}`

### 17.7 Admin APIs
- `GET /admin/products`
- `POST /admin/products`
- `PATCH /admin/products/{id}`
- `DELETE /admin/products/{id}`
- `GET /admin/categories`
- `POST /admin/categories`
- `PATCH /admin/categories/{id}`
- `GET /admin/customers`
- `PATCH /admin/customers/{id}`
- `GET /admin/orders`
- `GET /admin/orders/{id}`
- `PATCH /admin/orders/{id}/status`
- `GET /admin/reports/sales`
- `GET /admin/reports/top-products`
- `GET /admin/reports/customer-activity`
- `POST /admin/exports/orders`

### 17.8 Error Model
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Quantity must be greater than 0",
    "details": {
      "field": "quantity"
    }
  }
}
```

## 18. Security Requirements
- Enforce HTTPS across web and APIs.
- Use secure password hashing and token rotation.
- Implement RBAC for customers, admins, and super admins.
- Validate and sanitize all inputs.
- Protect against OWASP Top 10 risks including XSS, CSRF where relevant, SQL injection, and broken access control.
- Rate limit authentication and search endpoints.
- Support audit logs for admin actions.
- Use signed URLs or controlled upload endpoints for product images.
- Encrypt backups and define retention policies.
- Mask sensitive customer data in logs where possible.
- Ensure order and customer data access is scoped to the authenticated account.

## 19. Reporting Requirements
- Sales by day, week, month, and custom date range
- Order counts by status
- Top-selling products by quantity and revenue
- Most active customers by order count and value
- Exportable order reports in CSV and XLSX

## 20. Operational Requirements
- Centralized logging and monitoring
- Error tracking for clients and backend
- Uptime and latency dashboards
- Backup automation and restore testing
- Environment separation for dev, staging, production
- CI/CD pipeline for app, web, and backend deployments

## 21. Future Roadmap

### Phase 2
- Push notifications
- Customer-specific contract pricing
- Saved favorites and lists
- Advanced analytics dashboard
- Multi-language support
- Tax and invoice document generation

### Phase 3
- Online payment options
- ERP/accounting integration
- Warehouse and fulfillment integrations
- Sales rep assisted ordering
- Approval workflows for enterprise buyers

## 22. Acceptance Criteria

### 22.1 Customer Experience
- A new customer can register, log in, browse products, add items, and place an order successfully on Android, iOS, and web.
- A customer can search by product name and SKU and filter by category and availability.
- A customer sees correct tier pricing for all tested quantity ranges.
- Updating quantity on product page and cart updates unit price and line totals correctly.
- The checkout page displays the offline payment notice before order submission.
- After placing an order, the customer receives an order number and sees confirmation.
- Customers can view prior orders and reorder from history.

### 22.2 Admin Experience
- Admins can add, edit, delete products and manage product images, categories, availability, and pricing tiers from CMS.
- Admins can view customers, edit customer details, and activate/deactivate accounts.
- Admins can view all orders, update statuses, search, and filter successfully.
- Admins can access sales reports and export orders to CSV/XLSX.

### 22.3 System Quality
- Core APIs meet defined performance targets under expected load.
- Backups are configured and restore process is documented and tested.
- Role-based access prevents unauthorized access to admin features and customer data.
- Audit logs are generated for critical admin operations.

## 23. Release Plan

### MVP
- Authentication
- Catalog
- Pricing tiers
- Cart
- Checkout without online payment
- Order history
- Basic admin CMS
- Email notifications

### Launch Readiness Checklist
- Product and pricing data imported
- Notification templates approved
- Admin roles configured
- QA completed on Android, iOS, and web
- Reporting and exports validated
- Monitoring and backups active

## 24. Open Questions
- Will catalog browsing be public, or only visible after login?
- Are customer-specific prices needed after MVP?
- Are there multiple warehouses or shipping regions with different availability rules?
- Is SMS required at launch or only later?
- Are taxes shown separately, included, or deferred to manual invoicing?
- Is a single default currency sufficient for MVP?

## 25. Recommended Delivery Approach
- Build one shared backend and database from day one.
- Use React Native for mobile to reduce duplicated effort across Android and iOS.
- Build web and admin as React/Next.js applications sharing design system and API client patterns.
- Deliver in phases: MVP ordering first, then analytics, customer-specific pricing, and integrations.