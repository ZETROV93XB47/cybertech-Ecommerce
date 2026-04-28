/**
 * About-page hero. A two-column server component: brand statement on the left,
 * decorative image on the right with a soft glow background.
 */
export function AboutHero() {
  return (
    <section className="relative overflow-hidden bg-surface-container-low">
      <div className="container mx-auto px-6 md:px-8 py-24 grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
        <div className="space-y-6 z-10">
          <span className="font-label-caps text-secondary tracking-[0.2em] uppercase text-xs">
            Our Story
          </span>
          <h1 className="font-display text-[clamp(40px,6vw,68px)] leading-[1.05] font-bold text-primary">
            Hardware,
            <br />
            engineered for
            <br />
            those who care.
          </h1>
          <p className="font-body text-body-lg text-on-surface-variant max-w-lg">
            Cybertech curates and ships uncompromising computing gear to
            professionals, creators and enthusiasts who refuse to settle.
            Founded in 2024 in Lyon, we treat every component on our shelves
            as a tool worth obsessing over.
          </p>
        </div>
        <div className="relative h-[360px] lg:h-[520px] flex justify-center items-center">
          <div className="absolute w-[120%] h-[120%] bg-blue-50/60 rounded-full blur-3xl -z-10" />
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            alt="Cybertech workshop with engineers assembling a custom PC"
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuCmGt2DTuZYtQDwPGTbg_18BjJGe_IBQlgbh2BcQlchL2saFppmAmElSqRArGuPWlCRJRA1bkCIE62bp-lweP532UFemr11_ck1MI0ZiyTg160-_kyEsMbggnf-zOP0QiDHjzaiaKamBy4_0lyjzRASXhUrJBx2KvdDS3XG0WeoupolxC2bbUcU8v91iAD0N5MEqVKGGKK5zEHGk308Z4y1jz3TFbj6Tf_DUFFaw78878Pa9RBBd5oYuSAW-ouSIvBC5c4K1hzDhOog"
            className="w-full h-auto object-contain drop-shadow-2xl"
          />
        </div>
      </div>
    </section>
  );
}
