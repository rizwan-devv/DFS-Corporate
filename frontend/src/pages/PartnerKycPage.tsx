import { useParams } from 'react-router-dom';

/** Portal partner KYC disabled — partners use the mobile app after merchant submit. */
export function PartnerKycPage() {
  const { token } = useParams<{ token: string }>();

  return (
    <div className="page">
      <div className="container">
        <div className="panel panel--auth">
          <div className="badge">MOBILE APP ONLY</div>
          <h2 className="panel-title">Partner KYC moved to the app</h2>
          <p className="muted">
            Portal partner KYC links are disabled. After the merchant submits the application,
            each partner receives an email with the <strong>DFS Corporate mobile app</strong> link,
            phone as user ID, and a temporary PIN for biometric / video KYC.
          </p>
          {token && (
            <p className="muted" style={{ fontSize: '0.8rem', wordBreak: 'break-all' }}>
              Legacy token (not used): {token}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
