import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { HomePage } from './pages/HomePage';
import { GettingStartedPage } from './pages/GettingStartedPage';
import { SignupPage } from './pages/SignupPage';
import { FranchiseOnboardPage } from './pages/FranchiseOnboardPage';
import { VerifyOtpPage } from './pages/VerifyOtpPage';
import { LoginPage } from './pages/LoginPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { PartnerKycPage } from './pages/PartnerKycPage';
import { AdminPage } from './pages/AdminPage';
import { DashboardPage } from './pages/DashboardPage';
import { InvitesPage } from './pages/InvitesPage';
import { FranchisesPage } from './pages/FranchisesPage';
import { BalancePage } from './pages/BalancePage';
import { StatementPage } from './pages/StatementPage';
import { TransactionsPage } from './pages/TransactionsPage';
import { CardsPage } from './pages/CardsPage';
import { TransfersPage } from './pages/TransfersPage';
import { FtTransferPage, IbftTransferPage } from './pages/transfers/AccountRailTransferPage';
import { UbpTransferPage } from './pages/transfers/UbpTransferPage';
import { RaastTransferPage } from './pages/transfers/RaastTransferPage';
import { BeneficiariesPage } from './pages/BeneficiariesPage';
import { ProfilePage } from './pages/ProfilePage';
import { PortalUsersPage } from './pages/PortalUsersPage';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { SignaturePage } from './pages/SignaturePage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { ThemeProvider } from './theme/ThemeContext';

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/getting-started" element={<GettingStartedPage />} />
              <Route path="/signup" element={<SignupPage />} />
              <Route path="/franchise-onboard" element={<FranchiseOnboardPage />} />
              <Route path="/verify-otp" element={<VerifyOtpPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/change-password" element={<ChangePasswordPage />} />
              <Route path="/onboarding" element={<OnboardingPage />} />
              <Route path="/partner-kyc/:token" element={<PartnerKycPage />} />
              <Route path="/admin" element={<AdminPage />} />
              <Route path="/admin/review/:partyId" element={<AdminPage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/signature/:appUserId" element={<SignaturePage />} />
              <Route path="/invites" element={<InvitesPage />} />
              <Route path="/franchises" element={<FranchisesPage />} />
              <Route path="/balance" element={<BalancePage />} />
              <Route path="/statement" element={<StatementPage />} />
              <Route path="/transactions" element={<TransactionsPage />} />
              <Route path="/cards" element={<CardsPage />} />
              <Route path="/transfers" element={<TransfersPage />} />
              <Route path="/transfers/ft" element={<FtTransferPage />} />
              <Route path="/transfers/ibft" element={<IbftTransferPage />} />
              <Route path="/transfers/ubp" element={<UbpTransferPage />} />
              <Route path="/transfers/raast" element={<RaastTransferPage />} />
              <Route path="/beneficiaries" element={<BeneficiariesPage />} />
              <Route path="/portal-users" element={<PortalUsersPage />} />
              <Route path="/approvals" element={<ApprovalsPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
