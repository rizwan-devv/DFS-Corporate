import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../components/PageHeader';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type VirtualOrder = {
  id: number;
  relationshipNum?: string;
  embossName: string;
  status: string;
  pan?: string;
  maskedPan?: string;
  cvv?: string;
  last4?: string;
  expiry?: string;
  decisionNote?: string;
  printable?: boolean;
};

export function VirtualCardsPage() {
  const { session } = useAuth();
  const [relationshipNum, setRelationshipNum] = useState('');
  const [order, setOrder] = useState<VirtualOrder | null>(null);
  const [embossName, setEmbossName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [flipped, setFlipped] = useState(false);

  const load = useCallback(async () => {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      const res = await api<{ relationshipNum?: string; order?: VirtualOrder | null }>(
        '/api/cards/virtual',
        { token: session.token },
      );
      setRelationshipNum(res.relationshipNum || '');
      setOrder(res.order ?? null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load virtual card');
    } finally {
      setLoading(false);
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setOk('');
    setLoading(true);
    try {
      await api('/api/cards/virtual/order', {
        method: 'POST',
        token: session!.token,
        body: JSON.stringify({ embossName }),
      });
      setOk('Request sent. Back office will approve this virtual card.');
      setEmbossName('');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Order failed');
    } finally {
      setLoading(false);
    }
  }

  const approved = order?.status === 'APPROVED';
  const pending = order?.status === 'PENDING';

  return (
    <div className="portal-page virtual-card-page">
      <PageHeader
        eyebrow="Cards · Virtual"
        title="Virtual card"
        subtitle="Order here. Back office approves a PayFast virtual card. It is not printed."
      />

      {error && <p className="api-banner">{error}</p>}
      {ok && <div className="alert alert-ok">{ok}</div>}

      <p className="muted">
        Relationship <span className="mono">{relationshipNum || '—'}</span>
        {' · '}one virtual card per account. A physical card can still exist on the same relationship.
      </p>

      {approved && order && (
        <section className="card-stage">
          <button
            type="button"
            className={`plastic-card card-payfast ${flipped ? 'is-flipped' : ''}`}
            onClick={() => setFlipped((f) => !f)}
            aria-label="Flip PayFast virtual card"
          >
            <div className="plastic-face plastic-front">
              <div className="plastic-top">
                <span className="plastic-brand">
                  <img src="/payfast-mark.svg" alt="" className="plastic-brand-mark" />
                  PayFast
                </span>
                <span className="plastic-product">Virtual</span>
              </div>
              <div className="plastic-chip" aria-hidden />
              <div className="plastic-pan mono">{order.pan || order.maskedPan || '—'}</div>
              <div className="plastic-bottom">
                <div>
                  <span className="plastic-label">Card holder</span>
                  <strong>{order.embossName}</strong>
                </div>
                <div>
                  <span className="plastic-label">Valid</span>
                  <strong>{order.expiry || '—'}</strong>
                </div>
              </div>
            </div>
            <div className="plastic-face plastic-back">
              <div className="plastic-stripe" />
              <div className="plastic-cvv-box">
                <span>CVV</span>
                <strong className="mono">{order.cvv || '—'}</strong>
              </div>
              <p className="plastic-back-note">PayFast virtual card. Not printable.</p>
            </div>
          </button>
          <p className="muted center-hint">Click the card to see the CVV. Printing is disabled for virtual cards.</p>
        </section>
      )}

      {pending && order && (
        <section className="glass-panel">
          <h2 className="panel-title">Waiting for back office</h2>
          <p className="muted">
            {order.embossName} · relationship <span className="mono">{order.relationshipNum}</span> · status {order.status}
          </p>
        </section>
      )}

      {!approved && !pending && (
        <section className="glass-panel">
          <h2 className="panel-title">Order a virtual card</h2>
          <p className="muted">Name on the card. Back office approves the request. Nothing is sent to CMS.</p>
          <form className="form-grid" onSubmit={(e) => void submit(e)}>
            <div className="form-row">
              <label>Name on card</label>
              <input
                required
                maxLength={26}
                value={embossName}
                onChange={(e) => setEmbossName(e.target.value)}
                placeholder="AS ON THE CARD"
              />
            </div>
            <button className="btn btn-primary" type="submit" disabled={loading || !session}>
              {loading ? 'Sending…' : 'Submit order'}
            </button>
          </form>
        </section>
      )}
    </div>
  );
}
