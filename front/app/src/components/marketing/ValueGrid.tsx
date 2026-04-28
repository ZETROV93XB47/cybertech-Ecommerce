import { Icon } from "@/components/ui/Icon";

const VALUES = [
  {
    icon: "speed",
    title: "Performance",
    blurb:
      "Every product is benchmarked against its category's top tier. If it doesn't ship faster, render harder, or last longer, we don't list it.",
  },
  {
    icon: "ev_shadow",
    title: "Design",
    blurb:
      "Hardware is something you live with. We curate gear that earns its place on a desk — engineered, considered, and quietly beautiful.",
  },
  {
    icon: "hub",
    title: "Durability",
    blurb:
      "Premium isn't disposable. Our catalog leans toward repairable builds, long-life batteries and components rated for thousands of hours.",
  },
  {
    icon: "support_agent",
    title: "People first",
    blurb:
      "A six-person team handles every order, RMA and edge case in person. No outsourced tier-1 nonsense, ever.",
  },
];

/**
 * Four-up brand-value cards for the About page. Each card renders a Material
 * Symbol, a short title, and a paragraph blurb.
 */
export function ValueGrid() {
  return (
    <section className="py-24">
      <div className="container mx-auto px-6 md:px-8">
        <div className="text-center mb-12">
          <h2 className="font-display text-headline-md text-primary mb-2">
            What we obsess over
          </h2>
          <p className="text-on-surface-variant font-body text-body-md max-w-2xl mx-auto">
            Four principles drive every decision, from sourcing to packaging.
          </p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {VALUES.map((v) => (
            <article
              key={v.title}
              className="bg-white border border-slate-200 p-8 hover:border-secondary transition-colors"
            >
              <div className="w-12 h-12 flex items-center justify-center bg-primary text-on-primary mb-6">
                <Icon name={v.icon} size={24} />
              </div>
              <h3 className="font-display text-lg font-semibold text-primary mb-3">
                {v.title}
              </h3>
              <p className="font-body text-body-md text-on-surface-variant leading-relaxed">
                {v.blurb}
              </p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
