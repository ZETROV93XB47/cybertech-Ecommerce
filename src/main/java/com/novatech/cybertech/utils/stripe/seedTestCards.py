"""Give dev users a saved Stripe test card, without going through the frontend.

Every payment charges the card the user saved at Stripe (Customer + PaymentMethod). In the
browser that card is entered in Stripe Elements; this script does the same thing server-side
with Stripe's test PaymentMethod tokens, so dev accounts can pay right away.

For each user (looked up by email in the dev MySQL container):
  1. reuse its Stripe Customer, or create one (no personal data sent: only the user UUID);
  2. attach the chosen test card to it and make it the Customer's default;
  3. write the card in bankCardTable (replacing the previous one, detached at Stripe).

Re-run it after changing stripe.api.key (another Stripe account): customers unknown to the new
account are recreated.

Usage (from anywhere, the Docker stack running):
    python seedTestCards.py --email admin@email.com --email hideyoshi.tanaseda@email.com
    python seedTestCards.py --email someone@example.com --card declined
    python seedTestCards.py --list-cards

Cards (see docs/test-cards.md): visa (4242, paid), mastercard (4444, paid),
declined (0341, saved then declined), auth-required (3184, declined off-session).
"""

import argparse
import os
import re
import subprocess
import sys
import uuid
from pathlib import Path

import requests

STRIPE_API = "https://api.stripe.com/v1"
MYSQL_CONTAINER = os.environ.get("MYSQL_CONTAINER", "mysqlEcommerce")
MYSQL_DATABASE = os.environ.get("MYSQL_DATABASE", "cybertechDB")
MYSQL_USER = os.environ.get("MYSQL_USER", "root")
REQUEST_TIMEOUT_SECONDS = 30

# Stripe test PaymentMethod tokens: attaching one creates a new PaymentMethod of that test card.
TEST_CARDS = {
    "visa": ("pm_card_visa", "4242 4242 4242 4242 - payment succeeds"),
    "mastercard": ("pm_card_mastercard", "5555 5555 5555 4444 - payment succeeds"),
    "declined": ("pm_card_chargeCustomerFail", "4000 0000 0000 0341 - saved, every payment declined"),
    "auth-required": ("pm_card_authenticationRequired", "4000 0027 6000 3184 - declined off-session (authentication_required)"),
}

BRANDS = {"visa": "VISA", "mastercard": "MASTERCARD", "amex": "AMERICAN_EXPRESS", "discover": "DISCOVER"}

PROPERTIES_FILE = Path(__file__).resolve().parents[6] / "resources" / "application.properties"


def stripe_key() -> str:
    key = os.environ.get("STRIPE_API_KEY")
    if key:
        return key
    match = re.search(r"^stripe\.api\.key=(\S+)$", PROPERTIES_FILE.read_text(encoding="utf-8"), re.MULTILINE)
    if not match or match.group(1).startswith("${"):
        sys.exit("Set STRIPE_API_KEY (stripe.api.key is not a literal in application.properties)")
    return match.group(1)


class Stripe:
    def __init__(self, key: str):
        self.session = requests.Session()
        self.session.auth = (key, "")

    def call(self, method: str, path: str, **data) -> dict:
        response = self.session.request(method, STRIPE_API + path, data=data, timeout=REQUEST_TIMEOUT_SECONDS)
        body = response.json()
        if "error" in body:
            raise StripeError(response.status_code, body["error"])
        return body

    def customer_exists(self, customer_id: str) -> bool:
        try:
            return not self.call("GET", f"/customers/{customer_id}").get("deleted", False)
        except StripeError as error:
            if error.status == 404:
                return False
            raise


class StripeError(Exception):
    def __init__(self, status: int, error: dict):
        super().__init__(f"Stripe {status}: {error.get('code')} {error.get('message')}")
        self.status = status


def sql_literal(value) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def mysql(query: str) -> list[list[str]]:
    command = ["docker", "exec", "-i", MYSQL_CONTAINER, "mysql", f"-u{MYSQL_USER}", "-N", "-B", MYSQL_DATABASE]
    if os.environ.get("MYSQL_PWD"):
        command.insert(3, "-e")
        command.insert(4, f"MYSQL_PWD={os.environ['MYSQL_PWD']}")
    result = subprocess.run(command, input=query, capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        sys.exit(f"MySQL error: {result.stderr.strip()}")
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def seed(stripe: Stripe, email: str, card_name: str) -> None:
    rows = mysql(f"SELECT id, LOWER(HEX(uuid)), COALESCE(firstName,''), COALESCE(lastName,''), "
                 f"COALESCE(stripeCustomerId,'') FROM userTable WHERE email = {sql_literal(email)};")
    if not rows:
        print(f"  {email}: no such user, skipped")
        return
    user_id, uuid_hex, first_name, last_name, customer_id = rows[0]
    user_uuid = str(uuid.UUID(hex=uuid_hex))

    if not customer_id or not stripe.customer_exists(customer_id):
        customer_id = stripe.call("POST", "/customers", description="cybertech dev seed",
                                  **{"metadata[userUuid]": user_uuid})["id"]
        mysql(f"UPDATE userTable SET stripeCustomerId = {sql_literal(customer_id)} WHERE id = {user_id};")

    token, _ = TEST_CARDS[card_name]
    payment_method = stripe.call("POST", f"/payment_methods/{token}/attach", customer=customer_id)
    stripe.call("POST", f"/customers/{customer_id}", **{"invoice_settings[default_payment_method]": payment_method["id"]})
    card = payment_method["card"]
    holder = f"{first_name} {last_name}".strip() or email
    values = {
        "stripePaymentMethodId": payment_method["id"],
        "cardType": BRANDS.get(card["brand"], "OTHER"),
        "lastFourDigits": card["last4"],
        "expiryDate": f"{card['exp_month']:02d}/{card['exp_year']}",
        "cardHolderName": holder[:100],
    }

    existing = mysql(f"SELECT id, COALESCE(stripePaymentMethodId,'') FROM bankCardTable WHERE user_id = {user_id};")
    if existing:
        card_id, previous_pm = existing[0]
        assignments = ", ".join(f"{column} = {sql_literal(value)}" for column, value in values.items())
        mysql(f"UPDATE bankCardTable SET {assignments}, version = version + 1, updatedAt = NOW() WHERE id = {card_id};")
        if previous_pm and previous_pm != payment_method["id"]:
            try:
                stripe.call("POST", f"/payment_methods/{previous_pm}/detach")
            except StripeError as error:
                print(f"  (previous card {previous_pm} not detached: {error})")
    else:
        columns = ", ".join(values)
        literals = ", ".join(sql_literal(value) for value in values.values())
        mysql(f"INSERT INTO bankCardTable (uuid, version, createdAt, user_id, {columns}) "
              f"VALUES (UNHEX(REPLACE(UUID(), '-', '')), 0, NOW(), {user_id}, {literals});")
    print(f"  {email}: {card_name} **** {card['last4']} saved ({payment_method['id']}, customer {customer_id})")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--email", action="append", default=[], help="user email (repeatable)")
    parser.add_argument("--card", choices=sorted(TEST_CARDS), default="visa", help="test card to save (default: visa)")
    parser.add_argument("--list-cards", action="store_true", help="list the available test cards")
    args = parser.parse_args()

    if args.list_cards:
        for name, (_, description) in TEST_CARDS.items():
            print(f"{name:14} {description}")
        return
    if not args.email:
        parser.error("at least one --email is required")

    stripe = Stripe(stripe_key())
    print(f"Saving the '{args.card}' test card:")
    for email in args.email:
        try:
            seed(stripe, email, args.card)
        except StripeError as error:
            print(f"  {email}: {error}")


if __name__ == "__main__":
    main()
