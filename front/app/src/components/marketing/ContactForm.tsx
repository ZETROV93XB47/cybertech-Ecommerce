"use client";

import { useState, type FormEvent } from "react";
import { Icon } from "@/components/ui/Icon";

/**
 * Contact form. The backend exposes no /contact endpoint yet, so this form
 * is intentionally local-only: on submit it just validates the inputs and
 * shows an inline acknowledgement toast. We keep a `mailto:` fallback in the
 * sidebar of the page itself.
 *
 * When the backend ships POST /api/v1/services/contact, swap the body of
 * `onSubmit` for the real call.
 */
export function ContactForm() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [status, setStatus] = useState<"idle" | "submitting" | "sent" | "error">(
    "idle",
  );
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  function isEmail(value: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErrorMsg(null);

    if (name.trim().length < 2) {
      setStatus("error");
      setErrorMsg("Please enter your name.");
      return;
    }
    if (!isEmail(email.trim())) {
      setStatus("error");
      setErrorMsg("Please enter a valid email address.");
      return;
    }
    if (message.trim().length < 10) {
      setStatus("error");
      setErrorMsg("Please add at least a sentence so we can help.");
      return;
    }

    setStatus("submitting");
    // No backend endpoint yet — simulate a brief network hop then ack.
    window.setTimeout(() => {
      setStatus("sent");
      setName("");
      setEmail("");
      setMessage("");
    }, 600);
  }

  if (status === "sent") {
    return (
      <div className="border border-secondary/40 bg-secondary/5 p-8 flex items-start gap-4">
        <Icon
          name="check_circle"
          size={24}
          className="text-secondary mt-0.5"
          filled
        />
        <div>
          <h3 className="font-display text-lg font-semibold text-primary mb-1">
            Thanks — we&apos;ll get back to you.
          </h3>
          <p className="font-body text-body-md text-on-surface-variant">
            Our support team replies within one business day. If your matter
            is time-sensitive, email{" "}
            <a
              href="mailto:hello@cybertech.local"
              className="text-secondary underline-offset-4 hover:underline"
            >
              hello@cybertech.local
            </a>
            .
          </p>
          <button
            type="button"
            onClick={() => setStatus("idle")}
            className="mt-4 text-sm font-label-caps uppercase tracking-wider text-secondary hover:underline"
          >
            Send another
          </button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-2">
        <label
          htmlFor="contact-name"
          className="font-label-caps uppercase tracking-widest text-xs text-primary"
        >
          Name
        </label>
        <input
          id="contact-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          autoComplete="name"
          className="h-12 px-4 bg-white border border-slate-200 focus:ring-2 focus:ring-secondary/20 focus:border-secondary text-sm font-body outline-none"
          placeholder="Your name"
        />
      </div>
      <div className="flex flex-col gap-2">
        <label
          htmlFor="contact-email"
          className="font-label-caps uppercase tracking-widest text-xs text-primary"
        >
          Email
        </label>
        <input
          id="contact-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoComplete="email"
          className="h-12 px-4 bg-white border border-slate-200 focus:ring-2 focus:ring-secondary/20 focus:border-secondary text-sm font-body outline-none"
          placeholder="you@cybertech.dev"
        />
      </div>
      <div className="flex flex-col gap-2">
        <label
          htmlFor="contact-message"
          className="font-label-caps uppercase tracking-widest text-xs text-primary"
        >
          Message
        </label>
        <textarea
          id="contact-message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          required
          rows={6}
          className="px-4 py-3 bg-white border border-slate-200 focus:ring-2 focus:ring-secondary/20 focus:border-secondary text-sm font-body outline-none resize-y"
          placeholder="How can we help?"
        />
      </div>

      {status === "error" && errorMsg && (
        <div
          role="alert"
          className="flex items-start gap-2 text-sm text-error font-body"
        >
          <Icon name="error" size={16} className="mt-0.5" />
          <span>{errorMsg}</span>
        </div>
      )}

      <div className="flex items-center gap-4 pt-2">
        <button
          type="submit"
          disabled={status === "submitting"}
          className="inline-flex items-center justify-center gap-2 h-12 px-8 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors disabled:opacity-60"
        >
          {status === "submitting" ? "Sending…" : "Send message"}
        </button>
        <a
          href="mailto:hello@cybertech.local"
          className="text-sm text-secondary font-label-caps uppercase tracking-wider hover:underline"
        >
          or email us directly
        </a>
      </div>
    </form>
  );
}
