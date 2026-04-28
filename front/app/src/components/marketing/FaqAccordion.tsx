import { Icon } from "@/components/ui/Icon";

export type FaqItem = {
  question: string;
  answer: string;
};

export type FaqCategory = {
  title: string;
  items: FaqItem[];
};

/**
 * Accessible FAQ accordion built on the native <details>/<summary> elements
 * — no client JavaScript required. Items group under a heading per category.
 *
 *   <FaqAccordion categories={[{ title: "Orders", items: [{ question, answer }] }]} />
 */
export function FaqAccordion({ categories }: { categories: FaqCategory[] }) {
  return (
    <div className="flex flex-col gap-12">
      {categories.map((cat) => (
        <section key={cat.title}>
          <h2 className="font-display text-headline-sm text-primary mb-6">
            {cat.title}
          </h2>
          <ul className="flex flex-col divide-y divide-slate-200 border-y border-slate-200">
            {cat.items.map((item, idx) => (
              <li key={`${cat.title}-${idx}`}>
                <details className="group">
                  <summary className="flex items-center justify-between gap-6 py-5 cursor-pointer list-none select-none">
                    <span className="font-display text-base md:text-lg text-primary font-semibold pr-4">
                      {item.question}
                    </span>
                    <Icon
                      name="add"
                      size={20}
                      className="text-on-surface-variant transition-transform duration-200 group-open:rotate-45 shrink-0"
                    />
                  </summary>
                  <div className="pb-5 -mt-1 font-body text-body-md text-on-surface-variant leading-relaxed">
                    {item.answer}
                  </div>
                </details>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
