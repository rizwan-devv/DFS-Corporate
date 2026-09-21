import { useEffect, useState } from 'react';

const slides = [
  {
    title: 'Corporate onboarding',
    body: 'SECP entities, SBP-aligned KYC, and admin review in one guided flow.',
    accent: 'Onboard',
  },
  {
    title: 'Franchise control',
    body: 'Invite child partners, confirm commission, and monitor wallet activity.',
    accent: 'Partners',
  },
  {
    title: 'Cards & accounts',
    body: 'CMS-style card views with masked PAN — decrypt via secured API later.',
    accent: 'Cards',
  },
  {
    title: 'Live finance views',
    body: 'Balance, statement, and transactions — designed for clarity at a glance.',
    accent: 'Finance',
  },
  {
    title: 'Dual-tone DFS Connect',
    body: 'Light and dark themes with ambient gradients tuned for long operator sessions.',
    accent: 'Design',
  },
  {
    title: 'Payfast brand mode',
    body: 'Navy sidebar, growth-green CTAs, and soft cool panels — Payfast colours as a fourth theme.',
    accent: 'Payfast',
  },
  {
    title: 'Secure reveal',
    body: 'Partners see only scoped fields; full unmask goes through audited corporate APIs.',
    accent: 'Trust',
  },
];

export function HeroSlideshow() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const id = window.setInterval(() => {
      setIndex((i) => (i + 1) % slides.length);
    }, 4200);
    return () => window.clearInterval(id);
  }, []);

  const slide = slides[index];

  return (
    <div className="hero-slideshow" aria-live="polite">
      <div className="hero-slideshow-glow" aria-hidden />
      <div key={index} className="hero-slide animate-fade">
        <span className="hero-slide-accent">{slide.accent}</span>
        <h3>{slide.title}</h3>
        <p>{slide.body}</p>
      </div>
      <div className="hero-slideshow-dots" role="tablist" aria-label="Highlights">
        {slides.map((s, i) => (
          <button
            key={s.title}
            type="button"
            role="tab"
            aria-selected={i === index}
            className={`hero-dot ${i === index ? 'active' : ''}`}
            onClick={() => setIndex(i)}
          />
        ))}
      </div>
    </div>
  );
}
