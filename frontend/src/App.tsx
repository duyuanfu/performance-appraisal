import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { MainLayout } from './components/Layout/MainLayout';
import LoginPage from './pages/Login';
import UsersPage from './pages/Users';
import { MonthlyAdminBatchesPage } from './pages/MonthlyAppraisal/AdminBatches';
import { LeaderScoringPage } from './pages/MonthlyAppraisal/LeaderScoring';
import { EmployeeResultPage } from './pages/MonthlyAppraisal/EmployeeResult';
import { AnnualProjectAdminBatchesPage } from './pages/AnnualProject/AdminBatches';
import { ExpertEvaluationPage } from './pages/AnnualProject/ExpertEvaluation';

// Role-based home redirector
const HomeRedirect: React.FC = () => {
  const { activeRole } = useAuth();
  switch (activeRole) {
    case 'SYS_ADMIN':
      return <Navigate to="/users" replace />;
    case 'ADMIN':
      return <Navigate to="/monthly-head-admin" replace />;
    case 'LEADER':
    case 'DEPT_HEAD':
    case 'DEPT_DEPUTY':
      return <Navigate to="/leader-scoring" replace />;
    case 'EXPERT':
      return <Navigate to="/expert-evaluation" replace />;
    case 'EMPLOYEE':
    default:
      return <Navigate to="/my-performance" replace />;
  }
};

// Route protection component
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { token, loading } = useAuth();

  if (loading) {
    return <div style={{ padding: 40, textAlign: 'center' }}>系统加载中...</div>;
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route
            path="/"
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<HomeRedirect />} />
            {/* Admin Routes */}
            <Route path="monthly-admin" element={<MonthlyAdminBatchesPage />} />
            <Route path="monthly-head-admin" element={<MonthlyAdminBatchesPage category="DEPT_HEAD" />} />
            <Route path="monthly-deputy-admin" element={<MonthlyAdminBatchesPage category="DEPT_DEPUTY" />} />
            <Route path="annual-admin" element={<AnnualProjectAdminBatchesPage batchType="TARGET_PROJECT" />} />
            <Route path="target-project-admin" element={<AnnualProjectAdminBatchesPage batchType="TARGET_PROJECT" />} />
            <Route path="special-innovation-admin" element={<AnnualProjectAdminBatchesPage batchType="SPECIAL_INNOVATION" />} />
            <Route path="users" element={<UsersPage />} />

            {/* Leader Routes */}
            <Route path="leader-scoring" element={<LeaderScoringPage />} />

            {/* Expert Routes */}
            <Route path="expert-evaluation" element={<ExpertEvaluationPage />} />

            {/* Employee Routes */}
            <Route path="my-performance" element={<EmployeeResultPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
