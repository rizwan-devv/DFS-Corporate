import { Link } from 'react-router-dom';

const steps = [
  'Choose Corporate Merchant or Sub-merchant and verify email (OTP = 2FA step).',
  'Provide entity details per SBP Framework §E — legal name, addresses, nature of business, and terms.',
  'Add associated persons (sole / small business) or send partner KYC invites (partnership / LLP).',
  'Upload Annex-C documents — entity-specific mandatory packs plus optional alternatives.',
  'Submit for review — admin approves within 5 working days and emails login credentials.',
];

export function GettingStartedPage() {
  return (
    <section className="section page">
      <div className="container">
        <div className="breadcrumb animate-in">Home / Getting Started</div>
        <h2 className="animate-in animate-in-delay-1">Before You Begin — Corporate KYC</h2>
        <p className="section-lead animate-in animate-in-delay-2">
          Aligned with State Bank of Pakistan Consolidated Customer Onboarding Framework
          for corporate entity accounts and Annex-C documentation.
        </p>
        <div className="panel animate-in animate-in-delay-3">
          <ul className="checklist">
            {steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ul>
          <div className="actions">
            <Link className="btn btn-primary" to="/signup">Continue to Signup</Link>
            <Link className="btn btn-ghost" to="/">Back to Home</Link>
          </div>
        </div>
      </div>
    </section>
  );
}
