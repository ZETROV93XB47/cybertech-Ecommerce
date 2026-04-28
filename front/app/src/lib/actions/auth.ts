"use server";

import { ApiError, userApi } from "@/lib/api";
import type {
  BankCardCreationRequestDto,
  CardType,
  Sex,
  UserCreateRequestDto,
} from "@/lib/api";

/**
 * Server actions for the public auth surface. Today only `register` lives
 * here — sign-in is delegated to Auth.js's hosted /api/auth/signin handler,
 * and password resets bounce out to Keycloak.
 *
 * The register action validates the multi-step payload server-side (mirrors
 * the client-side zod schema) so a tampered submit can't slip through, then
 * forwards to /api/v1/services/user/register which is annotated `anonymous`
 * in the api client.
 */

export type RegisterPayload = {
  // Step 1 — identity
  firstName: string;
  lastName: string;
  email: string;
  username: string;
  password: string;
  birthDate: string; // YYYY-MM-DD
  sex: Sex;
  // Step 2 — address
  street: string;
  city: string;
  zipCode: string;
  country: string;
  // Step 3 — optional bank card
  card: {
    cardHolderName: string;
    cardNumber: string;
    expiryDate: string; // MM/yyyy
    cvv: string;
    cardType: CardType;
  } | null;
};

export type RegisterResult =
  | { ok: true; uuid: string }
  | { ok: false; error: string; field?: keyof RegisterPayload | "card" };

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
const ZIP_RE = /^[A-Za-z0-9 -]{3,10}$/;
const EXPIRY_RE = /^(0[1-9]|1[0-2])\/\d{4}$/;
const CARD_RE = /^\d{13,19}$/;
const CVV_RE = /^\d{3,4}$/;
const SEX_VALUES: readonly Sex[] = ["M", "F", "OTHER"] as const;
const CARD_TYPES: readonly CardType[] = ["VISA", "MASTERCARD", "AMEX"] as const;

function validate(payload: RegisterPayload): RegisterResult | null {
  if (!payload.firstName.trim()) return { ok: false, error: "First name is required.", field: "firstName" };
  if (!payload.lastName.trim()) return { ok: false, error: "Last name is required.", field: "lastName" };
  if (!EMAIL_RE.test(payload.email)) return { ok: false, error: "Enter a valid email address.", field: "email" };
  if (payload.username.trim().length < 3) return { ok: false, error: "Username must be at least 3 characters.", field: "username" };
  if (!PASSWORD_RE.test(payload.password)) {
    return {
      ok: false,
      error: "Password must be 8+ chars with upper, lower and a digit.",
      field: "password",
    };
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(payload.birthDate)) {
    return { ok: false, error: "Enter a valid birth date.", field: "birthDate" };
  }
  if (!SEX_VALUES.includes(payload.sex)) {
    return { ok: false, error: "Select a valid option.", field: "sex" };
  }
  if (!payload.street.trim()) return { ok: false, error: "Street is required.", field: "street" };
  if (!payload.city.trim()) return { ok: false, error: "City is required.", field: "city" };
  if (!ZIP_RE.test(payload.zipCode)) return { ok: false, error: "Enter a valid zip code.", field: "zipCode" };
  if (!payload.country.trim()) return { ok: false, error: "Country is required.", field: "country" };

  if (payload.card) {
    if (!payload.card.cardHolderName.trim()) {
      return { ok: false, error: "Cardholder name is required.", field: "card" };
    }
    if (!CARD_RE.test(payload.card.cardNumber.replace(/\s+/g, ""))) {
      return { ok: false, error: "Card number must be 13–19 digits.", field: "card" };
    }
    if (!EXPIRY_RE.test(payload.card.expiryDate)) {
      return { ok: false, error: "Expiry must use MM/YYYY format.", field: "card" };
    }
    if (!CVV_RE.test(payload.card.cvv)) {
      return { ok: false, error: "CVV must be 3 or 4 digits.", field: "card" };
    }
    if (!CARD_TYPES.includes(payload.card.cardType)) {
      return { ok: false, error: "Select a valid card type.", field: "card" };
    }
  }
  return null;
}

export async function registerAction(
  payload: RegisterPayload,
): Promise<RegisterResult> {
  const invalid = validate(payload);
  if (invalid) return invalid;

  const card: BankCardCreationRequestDto | null = payload.card
    ? {
        cardHolderName: payload.card.cardHolderName.trim(),
        cardNumber: payload.card.cardNumber.replace(/\s+/g, ""),
        expiryDate: payload.card.expiryDate,
        cvv: payload.card.cvv,
        cardType: payload.card.cardType,
      }
    : null;

  const dto: UserCreateRequestDto = {
    email: payload.email.trim().toLowerCase(),
    password: payload.password,
    firstName: payload.firstName.trim(),
    lastName: payload.lastName.trim(),
    username: payload.username.trim(),
    sex: payload.sex,
    birthDate: payload.birthDate,
    address: {
      street: payload.street.trim(),
      city: payload.city.trim(),
      zipCode: payload.zipCode.trim(),
      country: payload.country.trim(),
    },
    bankCardCreationRequestDto: card,
  };

  try {
    const res = await userApi.register(dto);
    return { ok: true, uuid: res.id };
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 409) {
        return {
          ok: false,
          error:
            err.body?.message ??
            "An account already exists with that email or username.",
          field: "email",
        };
      }
      if (err.status === 400 && err.body?.message) {
        return { ok: false, error: err.body.message };
      }
      return {
        ok: false,
        error: err.body?.message ?? `Registration failed (HTTP ${err.status}).`,
      };
    }
    return {
      ok: false,
      error: "Network error — please try again in a moment.",
    };
  }
}
