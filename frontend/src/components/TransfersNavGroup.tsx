import { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { TRANSFER_PRODUCTS } from '../lib/transferTypes';

type Props = {
  includeBeneficiaries?: boolean;
};

export function TransfersNavGroup({ includeBeneficiaries = true }: Props) {
  const location = useLocation();
  const inTransfers =
    location.pathname === '/transfers'
    || location.pathname.startsWith('/transfers/')
    || location.pathname === '/beneficiaries';
  const [open, setOpen] = useState(inTransfers);

  useEffect(() => {
    if (inTransfers) setOpen(true);
  }, [inTransfers]);

  return (
    <div className={`portal-nav-group ${open ? 'is-open' : ''}`}>
      <button
        type="button"
        className={`portal-link portal-link--parent ${inTransfers ? 'active' : ''}`}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="portal-link-icon">↗</span>
        <span className="portal-link-label">Transfers</span>
        <span className={`portal-link-chevron ${open ? 'is-open' : ''}`} aria-hidden>
          ›
        </span>
      </button>

      {open && (
        <div className="portal-nav-sub" role="group" aria-label="Transfer products">
          {TRANSFER_PRODUCTS.map((p) => (
            <NavLink key={p.id} to={p.path} className="portal-link portal-link--sub">
              <span className="portal-sub-dot" aria-hidden />
              {p.title}
            </NavLink>
          ))}
          {includeBeneficiaries && (
            <NavLink to="/beneficiaries" className="portal-link portal-link--sub">
              <span className="portal-sub-dot" aria-hidden />
              Beneficiaries
            </NavLink>
          )}
        </div>
      )}
    </div>
  );
}
