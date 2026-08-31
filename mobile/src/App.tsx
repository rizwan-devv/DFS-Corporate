import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { SessionProvider } from './auth/SessionContext';
import { LoginPage } from './pages/LoginPage';
import { FlowPage } from './pages/FlowPage';
import { DonePage } from './pages/DonePage';

export default function App() {
  return (
    <SessionProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route path="/kyc" element={<LoginPage />} />
          <Route path="/flow" element={<FlowPage />} />
          <Route path="/done" element={<DonePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </SessionProvider>
  );
}
