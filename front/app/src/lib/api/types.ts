/**
 * Type definitions mirroring the Cybertech backend DTOs.
 * Source of truth: docs/FRONTEND_SITREP.md §7.
 *
 * Mapping rules:
 *   Java BigDecimal     -> string  (parse only at display)
 *   Java UUID           -> string
 *   Java LocalDate      -> string  (YYYY-MM-DD)
 *   Java LocalDateTime  -> string  (ISO-8601)
 *   Java enum           -> string-literal union
 */

// ---------- Pagination (Spring Data Page<T>) ----------
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 0-based current page
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  sort?: {
    sorted: boolean;
    unsorted: boolean;
    empty: boolean;
  };
  pageable?: unknown;
}

// ---------- Error envelope ----------
export interface ErrorResponseDto {
  message: string;
  httpStatusCode: number;
  errorCodeType: "FUNCTIONAL" | "TECHNICAL";
}

// ---------- Common enums ----------
export type DiscountType =
  | "NO_DISCOUNT"
  | "BLACK_FRIDAY"
  | "WINTER_SALES"
  | "SPRING_SALES"
  | "BUY_ONE_GET_ONE_FREE";

export type DiscountCalculationType =
  | "NONE"
  | "PERCENTAGE"
  | "FIXED_AMOUNT"
  | "BUY_ONE_GET_ONE_FREE";

export type OrderStatus =
  | "CREATED"
  | "PAID"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED"
  | "PAYMENT_FAILED";

export type PaymentType = "STRIPE" | "BANK_CARD";
export type ShippingType = "STANDARD" | "EXPRESS";
export type ShippingProvider = "DHL" | "FEDEX" | (string & {});
export type Sex = "M" | "F" | "OTHER";
export type Role = "USER" | "ADMIN";
export type CardType = "VISA" | "MASTERCARD" | "AMEX";

// ---------- Shared value objects ----------
export interface Address {
  street: string;
  city: string;
  zipCode: string;
  country: string;
}

// ---------- Product ----------
export interface ProductResponseDto {
  uuid: string;
  name: string;
  price: string;
  brand: string;
  category: string;
  photoUrl: string;
  description: string;
  attributes: Record<string, unknown>;
}

export interface ProductSearchRequestDto {
  query?: string;
  brand?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ProductCreateRequestDto {
  name: string;
  price: string;
  brand: string;
  category: string;
  description: string;
  attributes: Record<string, unknown>;
  photoUrl?: string;
}

export type ProductUpdateRequestDto = Partial<ProductCreateRequestDto>;

// ---------- Cart ----------
export interface CartItemResponseDto {
  cartItemUuid: string;
  productUuid: string;
  productName: string;
  quantity: number;
  unitPrice: string;
  lineItemTotalPrice: string;
}

export interface CartResponseDto {
  cartUuid: string;
  userUuid: string;
  items: CartItemResponseDto[];
  totalPrice: string;
}

export interface CartCreateRequestDto {
  items: Array<{
    productUuid: string;
    quantity: number;
  }>;
}

export interface CartUpdateRequestDto {
  items: Array<{
    cartItemUuid?: string;
    productUuid: string;
    quantity: number;
  }>;
}

export interface CartItemRemoveRequestDto {
  productUuid: string;
  quantity: number;
}

// ---------- Order ----------
export interface OrderItemResponseDto {
  orderItemUuid: string;
  productUuid: string;
  productName: string;
  quantity: number;
  unitPrice: string;
  lineItemTotalPrice: string;
}

export interface OrderResponseDto {
  uuid: string;
  userUuid: string;
  orderDate: string; // YYYY-MM-DD
  status: OrderStatus;
  totalAmount: string;
  shippingAddress: string;
  orderItems: OrderItemResponseDto[];
}

export interface OrderStatusDto {
  uuid: string;
  status: OrderStatus;
}

export interface OrderPlacingRequestDto {
  userUuid: string;
  paymentType: PaymentType;
  shippingType: ShippingType;
  shippingProvider: ShippingProvider;
  shippingStreet: string;
  shippingCity: string;
  shippingZipCode: string;
  shippingCountry: string;
  discountType: DiscountType;
}

export interface OrderCancellationRequestDto {
  orderUuid: string;
  reason?: string;
}

export interface OrderUpdateRequestDto {
  orderUuid: string;
  shippingStreet?: string;
  shippingCity?: string;
  shippingZipCode?: string;
  shippingCountry?: string;
}

// ---------- User ----------
export interface UserResponseDto {
  uuid: string;
  email: string;
  firstName: string;
  lastName: string;
  username: string;
  sex: Sex;
  address: Address;
  birthDate: string;
  role: Role;
  keycloakId: string;
}

export interface UserCreateRequestDto {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  username: string;
  sex: Sex;
  address: Address;
  birthDate: string; // YYYY-MM-DD
  bankCardCreationRequestDto: BankCardCreationRequestDto | null;
}

export interface UserUpdateRequestDto {
  uuid: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  sex?: Sex;
  address?: Address;
}

export interface UserRegistrationResponse {
  id: string;
  keycloakId: string;
}

// ---------- Bank card ----------
export interface BankCardResponseDto {
  uuid: string;
  cardHolderName: string;
  maskedNumber: string; // e.g. "•••• 4242"
  expiryDate: string; // MM/yyyy
  cardType: CardType;
  userUuid: string;
  isDefault: boolean;
}

export interface BankCardCreationRequestDto {
  cardHolderName: string;
  cardNumber: string;
  expiryDate: string;
  cvv: string;
  cardType: CardType;
  userUuid?: string;
}

export interface BankCardUpdateRequestDto {
  uuid: string;
  cardHolderName?: string;
  expiryDate?: string;
}

// ---------- Wishlist ----------
export interface WishlistResponseDto {
  uuid: string;
  product: ProductResponseDto;
  addedAt: string;
}

// ---------- Review ----------
export interface ReviewResponseDto {
  uuid: string;
  userUuid: string;
  productUuid: string;
  productName: string;
  rating: number; // 1–5
  comment: string | null;
}

export interface ReviewableProductDto {
  productUuid: string;
  orderUuid: string;
  productName: string;
  orderDate: string;
}

export interface ReviewCreateRequestDto {
  productUuid: string;
  orderUuid: string;
  rating: number;
  comment?: string;
}

export interface ReviewUpdateRequestDto {
  rating?: number;
  comment?: string;
}

// ---------- Discount ----------
export interface DiscountContext {
  discountType: DiscountType;
  calculationType: DiscountCalculationType;
  percentage: string | null;
  fixedAmount: string | null;
  minOrderAmount: string | null;
  maxDiscountAmount: string | null;
  startsAt: string | null;
  endsAt: string | null;
  priority: number | null;
}

export interface DiscountCampaignResponseDto extends DiscountContext {
  uuid: string;
  enabled: boolean;
}

export interface DiscountCampaignUpdateRequestDto {
  enabled?: boolean;
  percentage?: string | null;
  fixedAmount?: string | null;
  minOrderAmount?: string | null;
  maxDiscountAmount?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  priority?: number | null;
}

// ---------- Misc ----------
export interface UserEventDto {
  type: string;
  payload: Record<string, unknown>;
  timestamp?: string;
}
