import { useEffect } from 'react';
import {
  formatReceiptWhen,
  type PaymentReceiptModel,
} from '../lib/paymentReceipt';

type Props = {
  receipt: PaymentReceiptModel;
  onClose: () => void;
};

export function PaymentReceipt({ receipt, onClose }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  function printReceipt() {
    window.print();
  }

  return (
    <div className="receipt-overlay" role="dialog" aria-modal="true" aria-labelledby="receipt-title">
      <button type="button" className="receipt-backdrop" aria-label="Close receipt" onClick={onClose} />
      <div className={`receipt-sheet ${receipt.success ? 'receipt-sheet--ok' : 'receipt-sheet--fail'}`}>
        <div className="receipt-glow" aria-hidden />
        <header className="receipt-top">
          <div className="receipt-brand">
            <span className="receipt-brand-mark" aria-hidden />
            <div>
              <p className="receipt-eyebrow">DFS CORPORATE</p>
              <h2 id="receipt-title" className="receipt-title">Payment receipt</h2>
            </div>
          </div>
          <button type="button" className="receipt-close" onClick={onClose} aria-label="Close">×</button>
        </header>

        <div className={`receipt-status ${receipt.success ? 'is-ok' : 'is-fail'}`}>
          <span className="receipt-status-icon" aria-hidden>{receipt.success ? '✓' : '!'}</span>
          <div>
            <strong>{receipt.statusLabel}</strong>
            <span>{receipt.productLabel}</span>
          </div>
        </div>

        <div className="receipt-amount-block">
          <p className="receipt-amount-label">Amount</p>
          <p className="receipt-amount">
            <span className="receipt-currency">{receipt.currency}</span>
            {receipt.amountDisplay}
          </p>
          {(receipt.counterpartyTitle || receipt.counterpartySubtitle) && (
            <p className="receipt-party">
              {receipt.counterpartyTitle}
              {receipt.counterpartySubtitle ? (
                <span className="receipt-party-sub">{receipt.counterpartySubtitle}</span>
              ) : null}
            </p>
          )}
        </div>

        <dl className="receipt-grid">
          <div>
            <dt>Date & time</dt>
            <dd>{formatReceiptWhen(receipt.paidAt)}</dd>
          </div>
          {receipt.portalTxnRef && (
            <div>
              <dt>Portal reference</dt>
              <dd className="receipt-mono">{receipt.portalTxnRef}</dd>
            </div>
          )}
          {receipt.responseCode && (
            <div>
              <dt>Response</dt>
              <dd className="receipt-mono">{receipt.responseCode}</dd>
            </div>
          )}
          {receipt.rows.map((row) => (
            <div key={`${row.label}-${row.value}`}>
              <dt>{row.label}</dt>
              <dd className={/account|wallet|consumer|iban|from|to|code|imd/i.test(row.label) ? 'receipt-mono' : undefined}>
                {row.value}
              </dd>
            </div>
          ))}
        </dl>

        {receipt.networkRows.length > 0 && (
          <section className="receipt-network">
            <h3>Network references</h3>
            <dl className="receipt-grid receipt-grid--compact">
              {receipt.networkRows.map((row) => (
                <div key={row.label}>
                  <dt>{row.label}</dt>
                  <dd className="receipt-mono">{row.value}</dd>
                </div>
              ))}
            </dl>
          </section>
        )}

        {receipt.message && (
          <p className="receipt-message">{receipt.message}</p>
        )}

        <footer className="receipt-foot">
          <p className="receipt-foot-note">DFS Connect · Keep this receipt for your records</p>
          <div className="receipt-actions">
            <button type="button" className="btn btn-ghost btn-sm" onClick={printReceipt}>Print</button>
            <button type="button" className="btn btn-primary btn-sm" onClick={onClose}>Done</button>
          </div>
        </footer>
      </div>
    </div>
  );
}
