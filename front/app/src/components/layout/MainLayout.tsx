import { Header } from "./Header";
import { Footer } from "./Footer";

/**
 * Customer-facing layout shell: fixed header, content area offset by the
 * header's height, then the footer. Use as a wrapper from any page that
 * isn't part of the admin surface.
 */
export function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Header />
      <main className="pt-20 min-h-screen">{children}</main>
      <Footer />
    </>
  );
}
