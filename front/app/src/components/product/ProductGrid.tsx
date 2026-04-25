import type { ProductResponseDto } from "@/lib/api";
import { ProductCard } from "./ProductCard";
import { Icon } from "@/components/ui/Icon";

export function ProductGrid({ products }: { products: ProductResponseDto[] }) {
  if (products.length === 0) {
    return (
      <div className="col-span-full flex flex-col items-center text-center py-16">
        <div className="w-20 h-20 rounded-full bg-surface-container-low flex items-center justify-center mb-4">
          <Icon name="search_off" size={32} className="text-outline" />
        </div>
        <h3 className="font-display text-headline-sm mb-2">
          No products match these filters
        </h3>
        <p className="text-on-surface-variant max-w-sm">
          Try widening the price range, clearing brands, or browsing a
          different category.
        </p>
      </div>
    );
  }
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-gutter">
      {products.map((p) => (
        <ProductCard key={p.uuid} product={p} />
      ))}
    </div>
  );
}
