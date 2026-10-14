import { useBackendStatus } from '../context/BackendStatusContext';
import { Server, RefreshCw, AlertCircle } from 'lucide-react';

const BackendStatusBanner = () => {
  const { isWaking, isExhausted, retry } = useBackendStatus();

  if (!isWaking && !isExhausted) {
    return null;
  }

  if (isWaking) {
    return (
      <aside
        role="status"
        aria-live="polite"
        className="w-full bg-amber-950/80 border-b border-amber-800/60 px-4 py-2.5 text-amber-200 text-xs sm:text-sm flex items-center justify-center gap-2.5 transition-all shadow-md"
      >
        <Server className="animate-pulse text-amber-400 shrink-0" size={16} />
        <span>
          Starting the server — the first connection after inactivity can take a little longer.
        </span>
      </aside>
    );
  }

  if (isExhausted) {
    return (
      <aside
        role="alert"
        className="w-full bg-red-950/80 border-b border-red-800/60 px-4 py-2.5 text-red-200 text-xs sm:text-sm flex items-center justify-center gap-3 transition-all shadow-md"
      >
        <AlertCircle className="text-red-400 shrink-0" size={16} />
        <span>
          The server is taking longer than expected to start after inactivity. You can continue waiting or retry.
        </span>
        <button
          onClick={retry}
          type="button"
          className="flex items-center gap-1 bg-red-900/70 hover:bg-red-800 text-white font-medium px-2.5 py-1 rounded text-xs transition border border-red-700/60 cursor-pointer"
        >
          <RefreshCw size={12} />
          Retry
        </button>
      </aside>
    );
  }

  return null;
};

export default BackendStatusBanner;
