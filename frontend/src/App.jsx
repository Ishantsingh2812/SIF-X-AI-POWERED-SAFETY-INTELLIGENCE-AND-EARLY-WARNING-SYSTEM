/**
 * App.jsx - OIL SIF-Lens AI Frontend Single Page Application
 *
 * This React application provides the user interface for safety officers and field engineers
 * to interact with the SIF Precursor Detection prototype.
 *
 * Architecture & Flow:
 * 1. UI collects safety reports (text, report type, location) or selects preset benchmark cases.
 * 2. It submits the report via HTTP POST to the Spring Boot backend (`http://localhost:8080/api/reports/analyze`).
 * 3. Spring Boot forwards the report to the Python FastAPI AI service (`http://localhost:8000/predict`),
 *    which runs text preprocessing, TF-IDF feature extraction, and ML classification.
 * 4. The result (SIF status, precursor category, risk score 0-100, feature attributions, recommendations)
 *    is saved in the database and returned to this React component.
 * 5. React renders interactive KPI cards, bar charts, pie charts (via Recharts), and explainability breakdowns.
 */
import React, { useState, useEffect } from 'react';
import { 
  ShieldAlert, AlertTriangle, CheckCircle, Activity, 
  BarChart3, FileText, Search, RefreshCw, ChevronRight, 
  HelpCircle, HardHat, Info 
} from 'lucide-react';
import { 
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, 
  PieChart, Pie, Cell 
} from 'recharts';

// Base URL for the Spring Boot REST API
const RAW_API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const API_BASE = (RAW_API_BASE.endsWith('/') ? RAW_API_BASE.slice(0, -1) : RAW_API_BASE) + '/api';

/**
 * Pre-configured realistic benchmark cases representing diverse oil & gas field scenarios.
 * Safety evaluators can click these in the demo to immediately test different risk profiles.
 */
const PRESET_CASES = [
  {
    title: 'Case 1: Fall from Height (Critical)',
    type: 'Unsafe Act / Condition',
    location: 'OIL Rig 14, Duliajan',
    report: 'Worker fell 15 feet from an unsecured ladder without harness while painting exterior tank shell.'
  },
  {
    title: 'Case 2: Electrical Hazard / LOTO (Critical)',
    type: 'Unsafe Act',
    location: 'Gas Compression Plant 2, Moran',
    report: 'Electrician was working on an energized 480V breaker panel without lockout tagout or voltage testing.'
  },
  {
    title: 'Case 3: Confined Space / Gas (High Risk)',
    type: 'Unsafe Condition',
    location: 'Crude Oil Storage Tank Farm, Digboi',
    report: 'Two workers entered crude oil storage tank for cleaning without atmospheric gas testing or ventilation.'
  },
  {
    title: 'Case 4: Minor Housekeeping (Low Risk)',
    type: 'Near Miss',
    location: 'Field Office Corridor, Guwahati',
    report: 'Trash and empty cardboard boxes left in hallway near office doorway obstructing walkway.'
  }
];

// Color palette for the Pie Chart risk breakdown
const COLORS = ['#ef4444', '#f97316', '#3b82f6', '#10b981', '#8b5cf6', '#06b6d4', '#ec4899', '#6b7280'];

export default function App() {
  // Authentication state
  const [token, setToken] = useState(() => sessionStorage.getItem('token') || '');
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const stored = sessionStorage.getItem('user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  // Login form state
  const [loginUsername, setLoginUsername] = useState('admin');
  const [loginPassword, setLoginPassword] = useState('Admin@123');
  const [loginError, setLoginError] = useState(null);
  const [loginLoading, setLoginLoading] = useState(false);

  // Navigation tab state: 'dashboard', 'analyze', or 'result'
  const isFieldWorker = currentUser?.role === 'ROLE_FIELD_WORKER';
  const [activeTab, setActiveTab] = useState(isFieldWorker ? 'analyze' : 'dashboard');

  // Operational metrics state (total reports, SIF counts, risk distributions)
  const [stats, setStats] = useState(null);

  // List of historical safety reports retrieved from the database
  const [recentReports, setRecentReports] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loadingDashboard, setLoadingDashboard] = useState(false);

  // Form input state for analyzing a new incident
  const [reportText, setReportText] = useState('');
  const [reportType, setReportType] = useState('Unsafe Act');
  const [location, setLocation] = useState('Duliajan Operational Area');

  // Request processing and feedback states
  const [analyzing, setAnalyzing] = useState(false);
  const [currentResult, setCurrentResult] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);

  const handleLogout = () => {
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
    setToken('');
    setCurrentUser(null);
    setCurrentResult(null);
    setStats(null);
  };

  /**
   * Wrapper around fetch that adds the JWT Authorization header,
   * handles 401 Unauthenticated by clearing session, and handles 429 Too Many Requests.
   */
  const authFetch = async (url, options = {}) => {
    const headers = {
      ...(options.headers || {}),
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const res = await fetch(url, { ...options, headers });
    if (res.status === 401) {
      handleLogout();
      throw new Error('Session expired or unauthorized. Please log in again.');
    }
    return res;
  };

  const handleLogin = async (e) => {
    if (e) e.preventDefault();
    setLoginLoading(true);
    setLoginError(null);

    try {
      const res = await fetch(API_BASE + '/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: loginUsername, password: loginPassword })
      });

      if (!res.ok) {
        let errMessage = 'Invalid credentials';
        try {
          const errData = await res.json();
          if (errData && errData.message) errMessage = errData.message;
        } catch (_) {}
        throw new Error(errMessage);
      }

      const data = await res.json();
      sessionStorage.setItem('token', data.token);
      sessionStorage.setItem('user', JSON.stringify({ username: data.username, role: data.role }));
      setToken(data.token);
      setCurrentUser({ username: data.username, role: data.role });
      if (data.role === 'ROLE_FIELD_WORKER') {
        setActiveTab('analyze');
      } else {
        setActiveTab('dashboard');
      }
    } catch (err) {
      setLoginError(err.message || 'Login failed. Please check your credentials.');
    } finally {
      setLoginLoading(false);
    }
  };

  /**
   * React Lifecycle Hook: runs when token or currentPage changes.
   * Loads initial dashboard metrics and previous reports from the backend if authorized.
   */
  useEffect(() => {
    if (token && currentUser?.role !== 'ROLE_FIELD_WORKER') {
      fetchDashboardData(currentPage);
    }
  }, [token, currentPage, currentUser?.role]);

  /**
   * Fetches the current operational statistics and recent reports concurrently
   * using Promise.all to minimize latency on dashboard load or refresh.
   */
  const fetchDashboardData = async (page = 0) => {
    if (!token) return;
    setLoadingDashboard(true);
    try {
      // Execute both backend REST calls in parallel with JWT
      const [statsRes, reportsRes] = await Promise.all([
        authFetch(API_BASE + '/dashboard/stats'),
        authFetch(API_BASE + `/reports?page=${page}&size=10`)
      ]);
      if (statsRes.ok) {
        const statsData = await statsRes.json();
        setStats(statsData);
      }
      if (reportsRes.ok) {
        const reportsData = await reportsRes.json();
        // Support both paginated {content: [...], totalPages: N} and plain list responses
        if (reportsData && Array.isArray(reportsData.content)) {
          setRecentReports(reportsData.content);
          setTotalPages(reportsData.totalPages || 1);
        } else if (Array.isArray(reportsData)) {
          setRecentReports(reportsData);
          setTotalPages(1);
        }
      }
    } catch (err) {
      console.error('Failed to fetch dashboard data:', err);
    } finally {
      setLoadingDashboard(false);
    }
  };

  /**
   * Submits the user-entered safety report to the Spring Boot REST API for NLP analysis.
   * On success, updates currentResult, switches to the 'result' tab, and refreshes the dashboard.
   */
  const handleAnalyze = async (e) => {
    if (e) e.preventDefault(); // Prevent standard HTML form page reload
    if (!reportText.trim()) return;

    setAnalyzing(true);
    setErrorMsg(null);

    try {
      const res = await authFetch(API_BASE + '/reports/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          report: reportText,
          reportType: reportType,
          location: location
        })
      });

      if (!res.ok) {
        if (res.status === 429) {
          setErrorMsg('Rate limit exceeded: Too many requests. Please wait a minute before analyzing again.');
          return;
        }
        if (res.status === 503) {
          setErrorMsg('The AI service is starting up or unavailable. Please retry in a moment.');
          return;
        }
        let errDetail = 'Analysis request failed from backend.';
        try {
          const errData = await res.json();
          if (errData && errData.message) errDetail = errData.message;
        } catch (_) {}
        throw new Error(errDetail);
      }

      const data = await res.json();
      setCurrentResult(data);
      setCurrentPage(0);
      setActiveTab('result'); // Automatically navigate to explainability view
      if (currentUser?.role !== 'ROLE_FIELD_WORKER') {
        fetchDashboardData(0);   // Refresh dashboard KPIs in background
      }
    } catch (err) {
      if (!errorMsg) {
        setErrorMsg(err.message || 'Failed to analyze report. Please try again.');
      }
      console.error(err);
    } finally {
      setAnalyzing(false);
    }
  };

  /**
   * Populates the input form with a preset scenario (e.g., fall from height, electrical hazard)
   * allowing one-click demonstrations during the presentation.
   */
  const loadPreset = (preset) => {
    setReportText(preset.report);
    setReportType(preset.type);
    setLocation(preset.location);
    setActiveTab('analyze');
  };

  /**
   * Helper function returning styled badge components for each safety risk tier.
   */
  const getRiskBadge = (level) => {
    switch (level) {
      case 'CRITICAL':
        return <span className="px-3 py-1 bg-red-950 text-red-400 border border-red-800 rounded-full text-xs font-bold tracking-wide animate-pulse">CRITICAL RISK</span>;
      case 'HIGH':
        return <span className="px-3 py-1 bg-orange-950 text-orange-400 border border-orange-800 rounded-full text-xs font-bold tracking-wide">HIGH RISK</span>;
      case 'MEDIUM':
        return <span className="px-3 py-1 bg-yellow-950 text-yellow-400 border border-yellow-800 rounded-full text-xs font-bold tracking-wide">MEDIUM RISK</span>;
      default:
        return <span className="px-3 py-1 bg-emerald-950 text-emerald-400 border border-emerald-800 rounded-full text-xs font-bold tracking-wide">LOW RISK</span>;
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-gradient-to-tr from-amber-500 to-red-600 rounded-lg shadow-lg shadow-red-500/20">
              <ShieldAlert className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-tight bg-gradient-to-r from-white via-slate-200 to-amber-400 bg-clip-text text-transparent">
                OIL SIF-Lens AI
              </h1>
              <p className="text-xs text-slate-400">SIH AI/NLP Engine for SIF Precursor Detection & Risk Classification</p>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            {token && (
              <nav className="flex items-center space-x-2">
                {!isFieldWorker && (
                  <button
                    onClick={() => setActiveTab('dashboard')}
                    className={'px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center space-x-1.5 ' + (activeTab === 'dashboard' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30' : 'text-slate-400 hover:text-white')}
                  >
                    <BarChart3 className="w-4 h-4" />
                    <span>Dashboard</span>
                  </button>
                )}
                <button
                  onClick={() => setActiveTab('analyze')}
                  className={'px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center space-x-1.5 ' + (activeTab === 'analyze' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30' : 'text-slate-400 hover:text-white')}
                >
                  <FileText className="w-4 h-4" />
                  <span>Analyze Report</span>
                </button>
                {currentResult && (
                  <button
                    onClick={() => setActiveTab('result')}
                    className={'px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center space-x-1.5 ' + (activeTab === 'result' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30' : 'text-slate-400 hover:text-white')}
                  >
                    <Activity className="w-4 h-4" />
                    <span>Result & Explainability</span>
                  </button>
                )}
              </nav>
            )}

            {currentUser && (
              <div className="flex items-center pl-3 space-x-3 border-l border-slate-800">
                <div className="text-right">
                  <div className="text-xs font-semibold text-slate-200">{currentUser.username}</div>
                  <div className="text-[10px] text-amber-400 font-mono">
                    {currentUser.role.replace('ROLE_', '')}
                  </div>
                </div>
                <button
                  onClick={handleLogout}
                  className="px-2.5 py-1 text-xs bg-slate-800 hover:bg-red-950/40 hover:text-red-400 text-slate-400 rounded border border-slate-700 hover:border-red-800 transition"
                >
                  Logout
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full">
        {!token ? (
          <div className="max-w-md mx-auto mt-12 p-6 bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl">
            <div className="flex items-center space-x-3 mb-6">
              <div className="p-3 bg-amber-500/10 text-amber-400 rounded-xl border border-amber-500/20">
                <HardHat className="w-6 h-6" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white">System Authentication</h2>
                <p className="text-xs text-slate-400">Log in to OIL SIF-Lens AI Platform</p>
              </div>
            </div>

            {loginError && (
              <div className="mb-4 p-3 bg-red-950/50 border border-red-800 rounded-lg text-xs text-red-300">
                {loginError}
              </div>
            )}

            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Username</label>
                <input
                  type="text"
                  required
                  value={loginUsername}
                  onChange={(e) => setLoginUsername(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-sm text-slate-200 focus:outline-none focus:border-amber-500"
                  placeholder="admin"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Password</label>
                <input
                  type="password"
                  required
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-sm text-slate-200 focus:outline-none focus:border-amber-500"
                  placeholder="••••••••"
                />
              </div>

              <button
                type="submit"
                disabled={loginLoading}
                className="w-full py-2.5 px-4 bg-amber-500 hover:bg-amber-600 disabled:opacity-50 text-slate-950 font-bold rounded-lg text-sm transition shadow-lg shadow-amber-500/10"
              >
                {loginLoading ? 'Authenticating...' : 'Sign In'}
              </button>
            </form>

            <div className="mt-6 pt-4 border-t border-slate-800 text-[11px] text-slate-500 space-y-1">
              <div className="font-semibold text-slate-400">Default Credentials:</div>
              <div>Admin: <span className="font-mono text-slate-300">admin / Admin@123</span></div>
              <div>Field Worker: <span className="font-mono text-slate-300">worker / Worker@123</span></div>
            </div>
          </div>
        ) : (
          <>
            {activeTab === 'dashboard' && !isFieldWorker && (
          <div className="space-y-6">
            <div className="flex justify-between items-center">
              <div>
                <h2 className="text-xl font-bold text-white">Operational Safety Dashboard</h2>
                <p className="text-sm text-slate-400">Trained on 105,996 real safety incident records from OSHA / BLS repository</p>
              </div>
              <button
                onClick={fetchDashboardData}
                disabled={loadingDashboard}
                className="flex items-center space-x-2 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs border border-slate-700 transition"
              >
                <RefreshCw className={'w-3.5 h-3.5 ' + (loadingDashboard ? 'animate-spin' : '')} />
                <span>Refresh</span>
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
                <div className="flex items-center justify-between text-slate-400 text-xs font-medium uppercase tracking-wider">
                  <span>Total Reports Analyzed</span>
                  <FileText className="w-4 h-4 text-slate-400" />
                </div>
                <div className="mt-2 text-2xl font-black text-white">
                  {stats ? stats.totalReports : '...'}
                </div>
                <div className="mt-1 text-xs text-slate-400">Stored in database</div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
                <div className="flex items-center justify-between text-amber-400 text-xs font-medium uppercase tracking-wider">
                  <span>SIF Precursors Detected</span>
                  <AlertTriangle className="w-4 h-4 text-amber-400" />
                </div>
                <div className="mt-2 text-2xl font-black text-amber-400">
                  {stats ? stats.sifPrecursors : '...'}
                </div>
                <div className="mt-1 text-xs text-amber-500/80">Elevated severity hazards</div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
                <div className="flex items-center justify-between text-orange-400 text-xs font-medium uppercase tracking-wider">
                  <span>High Risk Reports</span>
                  <ShieldAlert className="w-4 h-4 text-orange-400" />
                </div>
                <div className="mt-2 text-2xl font-black text-orange-400">
                  {stats ? stats.highRiskReports : '...'}
                </div>
                <div className="mt-1 text-xs text-orange-500/80">Score: 50 - 79</div>
              </div>

              <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
                <div className="flex items-center justify-between text-red-400 text-xs font-medium uppercase tracking-wider">
                  <span>Critical Risk Reports</span>
                  <Activity className="w-4 h-4 text-red-400" />
                </div>
                <div className="mt-2 text-2xl font-black text-red-400">
                  {stats ? stats.criticalReports : '...'}
                </div>
                <div className="mt-1 text-xs text-red-500/80">Score: 80 - 100</div>
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
                <h3 className="text-sm font-semibold text-slate-200 mb-4 flex items-center space-x-2">
                  <BarChart3 className="w-4 h-4 text-amber-400" />
                  <span>Precursor Category Breakdown</span>
                </h3>
                <div className="h-64">
                  {stats && stats.precursorDistribution && Object.keys(stats.precursorDistribution).length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={Object.entries(stats.precursorDistribution).map(([name, count]) => ({
                          name: name.replace(/_/g, ' '),
                          count
                        }))}
                        margin={{ top: 10, right: 10, left: -20, bottom: 25 }}
                      >
                        <XAxis dataKey="name" stroke="#64748b" fontSize={10} angle={-25} textAnchor="end" />
                        <YAxis stroke="#64748b" fontSize={11} allowDecimals={false} />
                        <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: 8, fontSize: 12 }} />
                        <Bar dataKey="count" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="h-full flex items-center justify-center text-slate-500 text-xs">
                      No precursor category data recorded yet.
                    </div>
                  )}
                </div>
              </div>

              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
                <h3 className="text-sm font-semibold text-slate-200 mb-4 flex items-center space-x-2">
                  <Activity className="w-4 h-4 text-red-400" />
                  <span>Risk Level Distribution</span>
                </h3>
                <div className="h-64 flex items-center justify-center">
                  {stats && stats.riskDistribution && Object.keys(stats.riskDistribution).length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={Object.entries(stats.riskDistribution).map(([name, value]) => ({ name, value }))}
                          cx="50%"
                          cy="50%"
                          innerRadius={50}
                          outerRadius={80}
                          paddingAngle={4}
                          dataKey="value"
                        >
                          {Object.keys(stats.riskDistribution).map((_, index) => (
                            <Cell key={'cell-' + index} fill={COLORS[index % COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: 8, fontSize: 12 }} />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="text-slate-500 text-xs">No risk level data available.</div>
                  )}
                </div>
              </div>
            </div>

            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
              <h3 className="text-sm font-semibold text-slate-200 mb-4 flex items-center space-x-2">
                <ShieldAlert className="w-4 h-4 text-amber-400" />
                <span>Recent Safety Reports & Classifications</span>
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="text-slate-400 bg-slate-950/60 uppercase tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-3">ID</th>
                      <th className="py-3 px-3">Report Description</th>
                      <th className="py-3 px-3">Precursor Category</th>
                      <th className="py-3 px-3">Risk Level</th>
                      <th className="py-3 px-3">Risk Score</th>
                      <th className="py-3 px-3">Location</th>
                      <th className="py-3 px-3">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800">
                    {recentReports.map((report) => (
                      <tr key={report.id} className="hover:bg-slate-800/40 transition">
                        <td className="py-3 px-3 font-mono text-slate-400">
                          #{report.id ? String(report.id).slice(-6) : ''}
                        </td>
                        <td className="py-3 px-3 text-slate-200 max-w-sm truncate" title={report.reportText}>
                          {report.reportText}
                        </td>
                        <td className="py-3 px-3 font-medium text-amber-400">
                          {report.precursorType ? report.precursorType.replace(/_/g, ' ') : 'N/A'}
                        </td>
                        <td className="py-3 px-3">{getRiskBadge(report.riskLevel)}</td>
                        <td className="py-3 px-3 font-mono font-bold">{report.riskScore != null ? report.riskScore + '/100' : 'N/A'}</td>
                        <td className="py-3 px-3 text-slate-400">{report.location || 'OIL Facility'}</td>
                        <td className="py-3 px-3">
                          <button
                            onClick={() => {
                              setCurrentResult(report);
                              setActiveTab('result');
                            }}
                            className="text-amber-400 hover:text-amber-300 font-medium flex items-center space-x-1"
                          >
                            <span>Inspect</span>
                            <ChevronRight className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      </tr>
                    ))}
                    {recentReports.length === 0 && (
                      <tr>
                        <td colSpan="7" className="text-center py-6 text-slate-500">
                          No safety reports logged yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {totalPages > 1 && (
                <div className="flex items-center justify-between px-3 py-3 border-t border-slate-800 text-xs text-slate-400">
                  <div>
                    Page <span className="text-white font-semibold">{currentPage + 1}</span> of <span className="text-white font-semibold">{totalPages}</span>
                  </div>
                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                      disabled={currentPage === 0}
                      className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 rounded border border-slate-700 transition"
                    >
                      Previous
                    </button>
                    <button
                      onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={currentPage >= totalPages - 1}
                      className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 rounded border border-slate-700 transition"
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'analyze' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <div>
              <h2 className="text-xl font-bold text-white">Analyze Safety Report (Near Miss / Unsafe Act)</h2>
              <p className="text-sm text-slate-400">
                Evaluates SIF precursor presence, Life-Saving Rule category, and risk level via the trained AI NLP engine.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-900 border border-slate-800">
              <h3 className="text-xs font-semibold text-amber-400 uppercase tracking-wider mb-2 flex items-center space-x-1.5">
                <HardHat className="w-4 h-4" />
                <span>Quick Test Scenarios (Sample Benchmark Cases)</span>
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-2">
                {PRESET_CASES.map((preset, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => loadPreset(preset)}
                    className="text-left p-2.5 rounded-lg bg-slate-950/70 border border-slate-800 hover:border-amber-500/50 transition group"
                  >
                    <div className="text-xs font-semibold text-slate-200 group-hover:text-amber-400">{preset.title}</div>
                    <div className="text-[11px] text-slate-400 truncate mt-0.5">{preset.report}</div>
                  </button>
                ))}
              </div>
            </div>

            {errorMsg && (
              <div className="p-3 bg-red-950 border border-red-800 rounded-lg text-xs text-red-300 flex items-center space-x-2">
                <AlertTriangle className="w-4 h-4 text-red-400 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <form onSubmit={handleAnalyze} className="p-6 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1">Report Type</label>
                  <select
                    value={reportType}
                    onChange={(e) => setReportType(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-amber-500"
                  >
                    <option value="Unsafe Act">Unsafe Act</option>
                    <option value="Unsafe Condition">Unsafe Condition</option>
                    <option value="Near Miss">Near Miss</option>
                    <option value="Incident Observation">Incident Observation</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-300 mb-1">Location / Facility</label>
                  <input
                    type="text"
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                    placeholder="e.g. OIL Rig 14, Duliajan"
                    className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-amber-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">
                  Report Description / Event Narrative <span className="text-amber-400">*</span>
                </label>
                <textarea
                  rows="4"
                  value={reportText}
                  onChange={(e) => setReportText(e.target.value)}
                  placeholder="Enter full safety observation or incident report narrative..."
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg p-3 text-sm text-slate-200 focus:outline-none focus:border-amber-500"
                  required
                />
              </div>

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  disabled={analyzing || !reportText.trim()}
                  className="px-5 py-2.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-slate-950 font-bold text-sm rounded-lg shadow-lg shadow-amber-500/20 disabled:opacity-50 flex items-center space-x-2 transition"
                >
                  {analyzing ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      <span>Running AI Pipeline...</span>
                    </>
                  ) : (
                    <>
                      <Activity className="w-4 h-4" />
                      <span>ANALYZE REPORT</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        )}

        {activeTab === 'result' && currentResult && (
          <div className="max-w-4xl mx-auto space-y-6">
            <div className="p-6 rounded-xl bg-slate-900 border border-slate-800 relative overflow-hidden">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">SIF Precursor Status</span>
                    {currentResult.sifPrecursorDetected ? (
                      <span className="px-2 py-0.5 bg-red-950 text-red-400 border border-red-800 rounded text-[11px] font-bold">
                        PRECURSOR DETECTED (YES)
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 bg-emerald-950 text-emerald-400 border border-emerald-800 rounded text-[11px] font-bold">
                        NO SIF PRECURSOR (NO)
                      </span>
                    )}
                  </div>
                  <h2 className="text-2xl font-black text-white mt-1">
                    {currentResult.precursorType ? currentResult.precursorType.replace(/_/g, ' ') : 'UNKNOWN'}
                  </h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Location: {currentResult.location || 'OIL Facility'} | Type: {currentResult.reportType || 'Safety Report'}
                  </p>
                </div>

                <div className="flex items-center space-x-6 sm:border-l sm:border-slate-800 sm:pl-6">
                  <div className="text-center">
                    <div className="text-xs text-slate-400 uppercase font-semibold">Risk Score</div>
                    <div className="text-3xl font-black text-amber-400 mt-0.5 font-mono">
                      {currentResult.riskScore != null ? currentResult.riskScore + '/100' : 'N/A'}
                    </div>
                  </div>
                  <div className="text-center">
                    <div className="text-xs text-slate-400 uppercase font-semibold">Risk Level</div>
                    <div className="mt-1">{getRiskBadge(currentResult.riskLevel)}</div>
                  </div>
                </div>
              </div>

              <div className="mt-4 p-3 bg-slate-950/70 border border-slate-800 rounded-lg text-xs text-slate-300 italic">
                "{currentResult.reportText}"
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
                <h3 className="text-sm font-semibold text-slate-200 mb-3 flex items-center space-x-2">
                  <CheckCircle className="w-4 h-4 text-amber-400" />
                  <span>Detected Hazard Factors</span>
                </h3>
                <ul className="space-y-2 text-xs text-slate-300">
                  {currentResult.detectedFactors && currentResult.detectedFactors.length > 0 ? (
                    currentResult.detectedFactors.map((factor, idx) => (
                      <li key={idx} className="flex items-start space-x-2">
                        <span className="text-amber-400 font-bold">✓</span>
                        <span>{factor}</span>
                      </li>
                    ))
                  ) : (
                    <li className="text-slate-500 italic">No specific high-energy factors flagged.</li>
                  )}
                </ul>
              </div>

              <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
                <h3 className="text-sm font-semibold text-slate-200 mb-3 flex items-center space-x-2">
                  <AlertTriangle className="w-4 h-4 text-red-400" />
                  <span>Potential Consequences</span>
                </h3>
                <ul className="space-y-2 text-xs text-slate-300">
                  {currentResult.potentialConsequences && currentResult.potentialConsequences.length > 0 ? (
                    currentResult.potentialConsequences.map((c, idx) => (
                      <li key={idx} className="flex items-start space-x-2">
                        <span className="text-red-400 font-bold">•</span>
                        <span>{c}</span>
                      </li>
                    ))
                  ) : (
                    <li className="text-slate-500 italic">Negligible or minor consequence expected.</li>
                  )}
                </ul>
              </div>
            </div>

            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
              <h3 className="text-sm font-semibold text-slate-200 flex items-center space-x-2">
                <HelpCircle className="w-4 h-4 text-amber-400" />
                <span>Why was this flagged? (Model-Derived Attribution)</span>
              </h3>

              <div className="space-y-1.5 text-xs text-slate-300 bg-slate-950/60 p-3.5 rounded-lg border border-slate-800">
                {currentResult.explanation && currentResult.explanation.map((reason, idx) => (
                  <p key={idx} className="leading-relaxed">
                    • {reason}
                  </p>
                ))}
              </div>

              {currentResult.topTerms && currentResult.topTerms.length > 0 && (
                <div>
                  <div className="text-xs font-semibold text-slate-400 mb-2 uppercase tracking-wide">
                    Salient Contributing Feature Tokens (Model Weights):
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {currentResult.topTerms.map((termObj, idx) => (
                      <div
                        key={idx}
                        className="px-2.5 py-1 bg-slate-800 border border-slate-700 rounded-md text-xs font-mono flex items-center space-x-1.5"
                      >
                        <span className="text-slate-200">{termObj.term}</span>
                        <span className="text-[10px] text-amber-400 bg-amber-950/80 px-1 py-0.2 rounded border border-amber-800/50">
                          +{termObj.weight}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
              <h3 className="text-sm font-semibold text-slate-200 mb-3 flex items-center space-x-2">
                <ShieldAlert className="w-4 h-4 text-emerald-400" />
                <span>Recommended Preventive Actions (Life-Saving Controls)</span>
              </h3>
              <ul className="space-y-2 text-xs text-slate-300">
                {currentResult.recommendedActions && currentResult.recommendedActions.length > 0 ? (
                  currentResult.recommendedActions.map((act, idx) => (
                    <li key={idx} className="flex items-start space-x-2">
                      <span className="text-emerald-400 font-bold">→</span>
                      <span>{act}</span>
                    </li>
                  ))
                ) : (
                  <li className="text-slate-500 italic">Maintain standard workplace housekeeping.</li>
                )}
              </ul>
            </div>

            <div className="p-3.5 bg-amber-950/30 border border-amber-800/50 rounded-xl text-xs text-amber-300/90 flex items-start space-x-2.5">
              <Info className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
              <div>
                <div className="font-semibold text-amber-300">Decision Support System Notice:</div>
                <div className="mt-0.5 text-amber-400/80">
                  {currentResult.decisionSupportDisclaimer ||
                    'This system is a decision-support prototype, NOT a definitive prediction of an accident. All results must be reviewed by certified safety personnel.'}
                </div>
              </div>
            </div>

            <div className="flex justify-between items-center pt-2">
              <button
                onClick={() => setActiveTab('analyze')}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700 transition"
              >
                ← Analyze Another Report
              </button>
              <button
                onClick={() => setActiveTab('dashboard')}
                className="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-slate-950 text-xs font-bold rounded-lg transition"
              >
                Go to Dashboard →
              </button>
            </div>
          </div>
        )}
          </>
        )}
      </main>

      <footer className="border-t border-slate-900 bg-slate-950 py-4 text-center text-xs text-slate-500">
        SIH Software Edition Prototype • Powered by Scikit-Learn TF-IDF, Spring Boot 3 & React
      </footer>
    </div>
  );
}

