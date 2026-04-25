---
name: Cybernetic Precision
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#45464d'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#76777d'
  outline-variant: '#c6c6cd'
  surface-tint: '#565e74'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#131b2e'
  on-primary-container: '#7c839b'
  inverse-primary: '#bec6e0'
  secondary: '#0058be'
  on-secondary: '#ffffff'
  secondary-container: '#2170e4'
  on-secondary-container: '#fefcff'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#191c1e'
  on-tertiary-container: '#818486'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#d8e2ff'
  secondary-fixed-dim: '#adc6ff'
  on-secondary-fixed: '#001a42'
  on-secondary-fixed-variant: '#004395'
  tertiary-fixed: '#e0e3e5'
  tertiary-fixed-dim: '#c4c7c9'
  on-tertiary-fixed: '#191c1e'
  on-tertiary-fixed-variant: '#444749'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display-lg:
    fontFamily: Space Grotesk
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-caps:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  container-max: 1280px
  gutter: 24px
  margin-mobile: 16px
  margin-desktop: 48px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
  section-gap: 80px
---

## Brand & Style

This design system is built on a foundation of **Minimalism** infused with **High-Contrast Modernism**. It targets a discerning audience that values technical excellence and premium craftsmanship. The emotional response is one of "industrial elegance"—cool, composed, and highly efficient.

The visual language balances the "Deep Slate" darkness of professional workstations with the "Electric Blue" energy of high-performance hardware. Extensive use of whitespace (negative space) is mandatory to ensure that the high-quality product imagery remains the focal point, preventing the interface from feeling cluttered or overwhelming.

## Colors

The palette is engineered to provide a sophisticated "pro" aesthetic. 

- **Primary (Deep Slate):** Used for text, primary navigation, and grounding structural elements. It provides more depth and luxury than pure black.
- **Secondary (Electric Blue):** A high-vibrancy blue reserved strictly for action-oriented elements, active states, and highlights.
- **Tertiary (Crisp White/Off-White):** Used for page backgrounds and container fills to maintain a clean, airy feel.
- **Neutrals:** A range of cool greys derived from the slate palette for borders, captions, and deactivated states.

High-end tech is often associated with dark modes; however, this system defaults to a "Clean White" mode to maximize readability and product clarity, using deep slates for contrast.

## Typography

This design system utilizes a dual-font strategy to balance technical character with functional clarity. 

**Space Grotesk** is used for headlines to provide a subtle "tech" edge with its geometric terminals. **Inter** is utilized for all body copy and UI labels to ensure maximum legibility across all screen densities. 

Hierarchy is established through significant size stepping and the use of uppercase letter-spacing for metadata and status labels. Tighten the tracking on larger headlines to maintain a premium, editorial feel.

## Layout & Spacing

The layout utilizes a **Fixed Grid** model for desktop to maintain control over line lengths and product gallery alignment. 

The rhythm is based on an **8px base unit**. Generous section gaps (80px+) are used to separate different product categories or content blocks, reinforcing the "premium" positioning. Layouts should favor asymmetrical compositions when showcasing flagship products to create a more dynamic, editorial experience.

## Elevation & Depth

Depth is achieved through **Ambient Shadows** and **Tonal Layers**. 

The system avoids heavy dropshadows in favor of "Soft Depth." Shadows should be highly diffused with a low opacity (e.g., `0, 0, 0, 0.05`) and a slight vertical offset to simulate a light source from above. 

Product cards use a very thin 1px border in a light slate tone, which transitions to a soft shadow on hover to indicate interactivity. This "Low-contrast Outline" approach keeps the UI flat and modern while providing enough tactile feedback for the user.

## Shapes

The shape language is defined by **Rounded** geometry. 

Standard components (buttons, input fields) use an 8px radius, while larger containers (product cards, modals) utilize a 12px-16px radius. This softening of the "tech" aesthetic prevents the interface from feeling too sterile or aggressive, making the high-end technology feel accessible and "human-centered."

Icons should follow a consistent "Linear" style with slightly rounded terminals to match the component corners.

## Components

### Buttons
Primary buttons utilize the Deep Slate background with Crisp White text. The "Electric Blue" is reserved for high-priority CTAs like "Add to Cart." Buttons should have a minimum height of 48px to feel substantial and premium.

### Premium Product Cards
Cards feature a "no-border" look on white backgrounds, defined by subtle shadows. Images should be edge-to-edge or set within a light grey (`#F1F5F9`) containment area to make product colors pop.

### Sophisticated Form Fields
Inputs use a minimal bottom-border or a very light 4-sided stroke. On focus, the stroke transitions to "Electric Blue" with a subtle outer glow (0px 0px 0px 4px rgba(59, 130, 246, 0.1)).

### Status Badges
Badges use a "dimmed" background of the status color with high-contrast text. 
- *Shipped*: Light Blue background / Electric Blue text.
- *Pending*: Light Slate background / Deep Slate text.
- *Delivered*: Light Emerald background / Emerald text.

### Product Detail Specifications
Use a clean, two-column grid with light horizontal dividers. Use the `label-caps` typography style for the spec titles to create a technical, "data-sheet" aesthetic.