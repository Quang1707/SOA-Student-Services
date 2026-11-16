# HỢP ĐỒNG GIAO TIẾP API TÍCH HỢP (API CONTRACT - TH6)

## 1. API Xác thực cấp phát Token (Auth Endpoint)
* **Endpoint:** `POST http://localhost:8080/api/v1/auth/login`
* **Request Body (`application/json`):**
  ```json
  {
    "username": "staff01",
    "password": "DemoPassword123!"
  }