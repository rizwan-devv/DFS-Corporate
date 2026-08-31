import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const parties = [
  {
    icon: '🏢',
    title: 'Corporate Merchant',
    desc: 'SECP-registered businesses onboarded with SBP-aligned corporate KYC (CDD + company documents).',
  },
  {
    icon: '🔗',
    title: 'Corporate Sub-merchant',
    desc: 'Businesses under an approved parent merchant, with parent authorization and identity KYC.',
  },
];

const entities = [
  { icon: '👤', title: 'Sole Proprietorship', desc: 'Single-owner business with letterhead / NTN alternatives.' },
  { icon: '💼', title: 'Small Business', desc: 'Freelance or small registered concerns.' },
  { icon: '🤝', title: 'Partnership', desc: 'Multi-partner firms with per-partner KYC invite links.' },
  { icon: '📋', title: 'LLP', desc: 'SECP-registered LLP with partner invite workflow.' },
];

export function HomePage() {
  const { session } = useAuth();
  const isAdmin = session?.role === 'PLATFORM_ADMIN';
  const isMerchant = !!session && !isAdmin;

  return (
    <>
      <section className="hero">
        <div className="container hero-grid">
          <div>
            <div className="breadcrumb animate-in">Home / Getting Started</div>
            <div className="badge animate-in animate-in-delay-1">CORPORATE KYC · SBP EMI</div>
            <h1 className="animate-in animate-in-delay-2">Start Building with DFS Corporate</h1>
            <p className="lead animate-in animate-in-delay-3">
              Open a corporate account as a merchant or sub-merchant. Complete SBP-aligned KYC,
              upload company documents, get admin review, and receive login credentials by email.
            </p>
            <div className="actions animate-in animate-in-delay-4">
              {isAdmin ? (
                <Link className="btn btn-primary" to="/admin">Open Backoffice</Link>
              ) : isMerchant ? (
                <>
                  <Link className="btn btn-primary" to="/dashboard">Go to Dashboard</Link>
                  {session.partyStatus !== 'ACTIVE' && (
                    <Link className="btn btn-ghost" to="/onboarding">My Application</Link>
                  )}
                </>
              ) : (
                <>
                  <Link className="btn btn-primary" to="/signup">Begin Corporate Onboarding</Link>
                  <Link className="btn btn-ghost" to="/getting-started">Before You Begin</Link>
                </>
              )}
            </div>
          </div>
          <div className="hero-visual animate-in animate-in-delay-2">
            <div className="hero-stat-grid">
              <div className="hero-stat">
                <strong>4</strong>
                <span>Entity types</span>
              </div>
              <div className="hero-stat">
                <strong>5 days</strong>
                <span>Review TAT</span>
              </div>
              <div className="hero-stat">
                <strong>§E</strong>
                <span>Framework aligned</span>
              </div>
              <div className="hero-stat">
                <strong>Link</strong>
                <span>Partner KYC</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <h2>Corporate accounts only</h2>
          <p className="section-lead">
            This product onboards corporate entities only — not retail wallets, agents, or individual customers.
          </p>
          <div className="grid-2">
            {parties.map((p) => (
              <article className="card" key={p.title}>
                <div className="card-icon" aria-hidden>{p.icon}</div>
                <h3>{p.title}</h3>
                <p>{p.desc}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section" style={{ paddingTop: 0 }}>
        <div className="container">
          <h2>Supported entity types</h2>
          <p className="section-lead">
            Annex-C categories 1–4 — sole prop, small business, partnership, and LLP.
          </p>
          <div className="grid-4">
            {entities.map((e) => (
              <article className="card" key={e.title}>
                <div className="card-icon" aria-hidden>{e.icon}</div>
                <h3>{e.title}</h3>
                <p>{e.desc}</p>
              </article>
            ))}
          </div>
          <div className="cta-band">
            <h3>Ready to onboard your entity?</h3>
            <p>Create an account, verify OTP, and complete KYC in guided steps.</p>
            <Link className="btn btn-primary" to="/signup">Get started</Link>
          </div>
        </div>
      </section>
    </>
  );
}
