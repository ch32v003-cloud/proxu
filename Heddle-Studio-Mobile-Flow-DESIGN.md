---
version: "alpha"
name: "Heddle Studio — Mobile Flow"
description: "Heddle Studio Testimonial Section is designed for showcasing social proof and customer credibility. Key features include reusable structure, responsive behavior, and production-ready presentation. It is suitable for component libraries and responsive product interfaces."
colors:
  primary: "#2366D8"
  secondary: "#D89F23"
  tertiary: "#6221E4"
  neutral: "#0A0A0A"
  background: "#2366D8"
  surface: "#0A0A0A"
  text-primary: "#A3A3A3"
  text-secondary: "#F8F9FA"
  border: "#FFFFFF"
  accent: "#2366D8"
typography:
  display-lg:
    fontFamily: "Instrument Serif"
    fontSize: "48px"
    fontWeight: 400
    lineHeight: "48px"
    letterSpacing: "-0.025em"
  body-md:
    fontFamily: "Inter"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
rounded:
  md: "12px"
spacing:
  base: "4px"
  sm: "2px"
  md: "4px"
  lg: "8px"
  xl: "12px"
  gap: "4px"
  card-padding: "12px"
  section-padding: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.border}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "0px"
  button-secondary:
    textColor: "{colors.border}"
    typography: "{typography.body-md}"
    rounded: "0px"
    padding: "6px"
  button-link:
    textColor: "{colors.text-primary}"
    rounded: "0px"
    padding: "6px"
  card:
    backgroundColor: "#1A1A1A"
    rounded: "{rounded.md}"
    padding: "16px"
---

## Overview

- **Composition cues:**
  - Layout: Grid
  - Content Width: Full Bleed
  - Framing: Glassy
  - Grid: Strong

## Colors

The color system uses dark mode with #2366D8 as the main accent and #0A0A0A as the neutral foundation.

- **Primary (#2366D8):** Main accent and emphasis color.
- **Secondary (#D89F23):** Supporting accent for secondary emphasis.
- **Tertiary (#6221E4):** Reserved accent for supporting contrast moments.
- **Neutral (#0A0A0A):** Neutral foundation for backgrounds, surfaces, and supporting chrome.

- **Usage:** Background: #2366D8; Surface: #0A0A0A; Text Primary: #A3A3A3; Text Secondary: #F8F9FA; Border: #FFFFFF; Accent: #2366D8

- **Gradients:** bg-gradient-to-b from-[#2366D8] to-transparent, bg-gradient-to-br from-white/5 to-transparent, bg-gradient-to-t from-[#0A0A0A] to-transparent via-[#0A0A0A]/95

## Typography

Typography pairs Instrument Serif for display hierarchy with Inter for supporting content and interface copy.

- **Display (`display-lg`):** Instrument Serif, 48px, weight 400, line-height 48px, letter-spacing -0.025em.
- **Body (`body-md`):** Inter, 14px, weight 400, line-height 20px.

## Layout

Layout follows a grid composition with reusable spacing tokens. Preserve the grid, full bleed structural frame before changing ornament or component styling. Use 4px as the base rhythm and let larger gaps step up from that cadence instead of introducing unrelated spacing values.

Treat the page as a grid / full bleed composition, and keep that framing stable when adding or remixing sections.

- **Layout type:** Grid
- **Content width:** Full Bleed
- **Base unit:** 4px
- **Scale:** 2px, 4px, 8px, 12px, 16px, 20px, 24px, 32px
- **Section padding:** 24px, 28px, 32px, 48px
- **Card padding:** 12px, 16px
- **Gaps:** 4px, 6px, 8px, 12px

## Elevation & Depth

Depth is communicated through glass, border contrast, and reusable shadow or blur treatments. Keep those recipes consistent across hero panels, cards, and controls so the page reads as one material system.

Surfaces should read as glass first, with borders, shadows, and blur only reinforcing that material choice.

- **Surface style:** Glass
- **Borders:** 1px #FFFFFF; 8px #171717; 1px #262626; 1px #0A0A0A
- **Shadows:** rgba(0, 0, 0, 0.3) 0px 25px 50px -12px, rgba(255, 255, 255, 0.05) 0px 0px 0px 1px inset; rgba(0, 0, 0, 0.2) 0px 2px 8px 0px, rgba(255, 255, 255, 0.02) 0px 1px 0px 0px inset; rgba(255, 255, 255, 0.2) 0px 2px 4px 0px inset
- **Blur:** 12px, 24px, 8px

### Techniques
- **Gradient border shell:** Use a thin gradient border shell around the main card. Wrap the surface in an outer shell with 22px padding and a 0px radius. Drive the shell with linear-gradient(to top, rgb(10, 10, 10), rgba(10, 10, 10, 0.95), rgba(0, 0, 0, 0)) so the edge reads like premium depth instead of a flat stroke. Keep the actual stroke understated so the gradient shell remains the hero edge treatment. Inset the real content surface inside the wrapper with a slightly smaller radius so the gradient only appears as a hairline frame.

## Shapes

Shapes rely on a tight radius system anchored by 4px and scaled across cards, buttons, and supporting surfaces. Icon geometry should stay compatible with that soft-to-controlled silhouette.

Use the radius family intentionally: larger surfaces can open up, but controls and badges should stay within the same rounded DNA instead of inventing sharper or pill-only exceptions.

- **Corner radii:** 4px, 8px, 12px, 16px, 40px, 9999px
- **Icon treatment:** Linear
- **Icon sets:** Solar

## Components

Anchor interactions to the detected button styles. Reuse the existing card surface recipe for content blocks.

### Buttons
- **Primary:** background #2366D8, text #FFFFFF, radius 12px, padding 0px, border 0px solid rgb(229, 231, 235).
- **Secondary:** text #FFFFFF, radius 0px, padding 6px, border 0px 0px 2px solid #2366D8.
- **Links:** text #A3A3A3, radius 0px, padding 6px, border 0px solid rgb(229, 231, 235).

### Cards and Surfaces
- **Card surface:** background #1A1A1A, border 1px solid rgb(38, 38, 38), radius 12px, padding 16px, shadow none.
- **Card surface:** background #141414, border 1px solid rgba(255, 255, 255, 0.05), radius 12px, padding 16px, shadow none.
- **Card surface:** background rgba(10, 10, 10, 0.8), radius 0px, padding 28px, shadow none, blur 12px.

### Iconography
- **Treatment:** Linear.
- **Sets:** Solar.

## Do's and Don'ts

Use these constraints to keep future generations aligned with the current system instead of drifting into adjacent styles.

### Do
- Do use the primary palette as the main accent for emphasis and action states.
- Do keep spacing aligned to the detected 4px rhythm.
- Do reuse the Glass surface treatment consistently across cards and controls.
- Do keep corner radii within the detected 4px, 8px, 12px, 16px, 40px, 9999px family.

### Don't
- Don't introduce extra accent colors outside the core palette roles unless the page needs a new semantic state.
- Don't mix unrelated shadow or blur recipes that break the current depth system.
- Don't exceed the detected minimal motion intensity without a deliberate reason.

## Motion

Motion stays restrained and interface-led across text, layout, and scroll transitions. Timing clusters around 150ms. Easing favors ease and cubic-bezier(0.4. Hover behavior focuses on text changes.

**Motion Level:** minimal

**Durations:** 150ms

**Easings:** ease, cubic-bezier(0.4, 0, 0.2, 1)

**Hover Patterns:** text
