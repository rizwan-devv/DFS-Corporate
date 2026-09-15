import { useEffect, useState } from 'react';

type Slide = { accent: string; title: string; body: string };

const DEFAULT_SLIDES: Slide[] = [
  {
    accent: 'Liquidity',
    title: 'Balances that breathe',
    body: 'Parent wallet and child agent balances in one calm, high-contrast strip.',
  },
  {
    accent: 'Movement',
    title: 'Statements with rhythm',
    body: 'Credit and debit flow with clear hierarchy — ready for AgentApp live data.',
  },
  {
    accent: 'Ledger',
    title: 'Commission clarity',
    body: 'Franchise settlements and shares, designed for operator speed.',
  },
  {
    accent: 'Cards',
    title: 'CMS-grade detail',
    body: 'Masked by default. Unmask only through secured inquiry with audit.',
  },
];

export function FinanceSlideshow({ slides = DEFAULT_SLIDES, intervalMs = 4200 }: { slides?: Slide[]; intervalMs?: number }) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const id = window.setInterval(() => setIndex((i) => (i + 1) % slides.length), intervalMs);
    return () => window.clearInterval(id);
  }, [slides.length, intervalMs]);

  const slide = slides[index];

  return (
    <div className="finance-slideshow glass-panel">
      <div className="finance-slideshow-orb" aria-hidden />
      <div key={index} className="finance-slide animate-fade">
        <span className="page-eyebrow">{slide.accent}</span>
        <h3>{slide.title}</h3>
        <p className="muted">{slide.body}</p>
      </div>
      <div className="hero-slideshow-dots">
        {slides.map((s, i) => (
          <button
            key={s.title}
            type="button"
            className={`hero-dot ${i === index ? 'active' : ''}`}
            aria-label={s.title}
            onClick={() => setIndex(i)}
          />
        ))}
      </div>
    </div>
  );
}
