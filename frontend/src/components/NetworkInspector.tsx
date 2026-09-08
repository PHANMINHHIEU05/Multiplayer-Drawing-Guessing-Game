import React, { useEffect, useState, useMemo } from 'react';
import { useMetricsStore, metricsStore, DrawingProtocolMode } from '../store/metricsStore';

export const NetworkInspector: React.FC = () => {
  const metrics = useMetricsStore((s) => s);
  const [isCollapsed, setIsCollapsed] = useState<boolean>(false);
  const [isVisible, setIsVisible] = useState<boolean>(true);
  const [showDetails, setShowDetails] = useState<boolean>(false);

  // Global hotkeys: Ctrl+Shift+N or ` (backtick) or Escape (to collapse/close)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'n') || e.key === '`') {
        e.preventDefault();
        setIsVisible((prev) => !prev);
      } else if (e.key === 'Escape' && isVisible) {
        if (!isCollapsed) {
          setIsCollapsed(true);
        } else {
          setIsVisible(false);
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isVisible, isCollapsed]);

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'CONNECTED':
        return {
          bg: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40',
          dot: 'bg-emerald-400',
          text: 'Connected',
        };
      case 'CONNECTING':
      case 'RECONNECTING':
        return {
          bg: 'bg-amber-500/20 text-amber-400 border-amber-500/40 animate-pulse',
          dot: 'bg-amber-400 animate-ping',
          text: status === 'RECONNECTING' ? 'Reconnecting' : 'Connecting',
        };
      default:
        return {
          bg: 'bg-rose-500/20 text-rose-400 border-rose-500/40',
          dot: 'bg-rose-500',
          text: 'Disconnected',
        };
    }
  };

  const statusBadge = useMemo(() => getStatusBadge(metrics.status), [metrics.status]);

  const getRttColor = (rtt: number, sampleCount: number) => {
    if (sampleCount === 0 || metrics.status !== 'CONNECTED') return 'text-slate-400';
    if (rtt < 150) return 'text-emerald-400';
    if (rtt <= 300) return 'text-amber-400';
    return 'text-rose-400';
  };

  // Re-open pill badge if completely closed
  if (!isVisible) {
    return (
      <button
        onClick={() => {
          setIsVisible(true);
          setIsCollapsed(false);
        }}
        className="fixed bottom-3 right-3 z-50 px-3 py-1.5 rounded-xl bg-slate-950/90 hover:bg-slate-900 backdrop-blur-md border border-slate-700/80 text-cyan-400 text-xs font-mono font-medium shadow-xl hover:shadow-cyan-950/50 transition-all flex items-center gap-2"
        title="Open Network Inspector (Ctrl+Shift+N or `)"
      >
        <span className={`w-2 h-2 rounded-full ${statusBadge.dot}`} />
        <span>⚡ NET</span>
        <span className="text-slate-400 text-[11px]">
          {metrics.rttSamplesCount > 0 ? `${metrics.rttCurrent}ms` : '—'}
        </span>
      </button>
    );
  }

  // Collapsed View: ultra-compact, non-intrusive floating chip
  if (isCollapsed) {
    return (
      <div className="fixed bottom-3 right-3 z-50 w-72 rounded-xl bg-slate-950/90 backdrop-blur-xl border border-slate-800/90 shadow-2xl text-slate-200 font-mono text-xs overflow-hidden select-none transition-all">
        <div
          onClick={() => setIsCollapsed(false)}
          className="flex items-center justify-between px-3 py-2 cursor-pointer hover:bg-slate-900/60 transition"
          title="Click to expand Network Inspector"
        >
          <div className="flex items-center gap-2">
            <span className="text-cyan-400 font-bold">⚡ Network</span>
            <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold border flex items-center gap-1 ${statusBadge.bg}`}>
              <span className={`w-1.5 h-1.5 rounded-full ${statusBadge.dot}`} />
              {statusBadge.text}
            </span>
          </div>
          <div className="flex items-center gap-2 text-[11px]">
            <span className="text-slate-400">
              RTT: <strong className={getRttColor(metrics.rttCurrent, metrics.rttSamplesCount)}>
                {metrics.rttSamplesCount > 0 ? `${metrics.rttCurrent}ms` : '—'}
              </strong>
            </span>
            <button
              onClick={(e) => {
                e.stopPropagation();
                setIsCollapsed(false);
              }}
              className="text-slate-400 hover:text-slate-200 p-0.5"
              title="Expand"
            >
              ▲
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                setIsVisible(false);
              }}
              className="text-slate-400 hover:text-rose-400 p-0.5"
              title="Close"
            >
              ✕
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Expanded View: structured 4-block layout
  return (
    <div className="fixed bottom-3 right-3 z-50 w-80 sm:w-[360px] rounded-2xl bg-slate-950/95 backdrop-blur-xl border border-slate-800 shadow-2xl text-slate-200 font-mono text-xs overflow-hidden select-none animate-fadeIn transition-all">
      {/* Header */}
      <div className="flex items-center justify-between px-3.5 py-2.5 bg-slate-900/90 border-b border-slate-800/80">
        <div className="flex items-center gap-2">
          <span className="text-cyan-400 font-bold tracking-wide flex items-center gap-1">
            ⚡ Inspector
          </span>
          <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold border flex items-center gap-1 ${statusBadge.bg}`}>
            <span className={`w-1.5 h-1.5 rounded-full ${statusBadge.dot}`} />
            {statusBadge.text}
          </span>
          <span className="text-[10px] text-slate-500 bg-slate-800/60 px-1.5 py-0.5 rounded">
            {metrics.gatewayId}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => metricsStore.reset()}
            className="px-2 py-0.5 rounded bg-slate-800/90 hover:bg-slate-700 text-slate-400 hover:text-slate-200 text-[10px] transition border border-slate-700/50"
            title="Reset telemetry counters"
          >
            Reset
          </button>
          <button
            onClick={() => setIsCollapsed(true)}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 text-xs"
            title="Collapse (or click outside)"
          >
            ▼
          </button>
          <button
            onClick={() => setIsVisible(false)}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-slate-800 text-slate-400 hover:text-rose-400 text-xs font-bold"
            title="Close Inspector (Ctrl+Shift+N or `)"
          >
            ✕
          </button>
        </div>
      </div>

      <div className="p-3 space-y-2.5 max-h-[65vh] overflow-y-auto custom-scrollbar">
        {/* Protocol Selector */}
        <div>
          <div className="flex justify-between items-center text-[10px] text-slate-400 font-semibold uppercase tracking-wider mb-1">
            <span>Protocol Mode</span>
            <span className="text-cyan-400 lowercase text-[9px]">{metrics.drawingMode.replace('_', ' ')}</span>
          </div>
          <div className="grid grid-cols-3 gap-1 bg-slate-900/90 p-1 rounded-xl border border-slate-800/90">
            {(['BINARY_BATCH', 'JSON_BATCH', 'JSON_POINT'] as DrawingProtocolMode[]).map((mode) => (
              <button
                key={mode}
                onClick={() => metricsStore.setDrawingMode(mode)}
                className={`py-1 px-1 rounded-lg text-[10px] font-semibold transition-all text-center ${
                  metrics.drawingMode === mode
                    ? 'bg-cyan-600 text-white shadow-sm shadow-cyan-600/40'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`}
              >
                {mode === 'BINARY_BATCH' ? 'Binary Batch' : mode === 'JSON_BATCH' ? 'JSON Batch' : 'JSON Point'}
              </button>
            ))}
          </div>
        </div>

        {/* 1. LATENCY BLOCK */}
        <div className="bg-slate-900/60 p-2.5 rounded-xl border border-slate-800/80 space-y-1.5">
          <div className="flex justify-between items-baseline">
            <span className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Latency</span>
            <div className="flex items-baseline gap-1">
              <span className={`text-base font-bold tracking-tight ${getRttColor(metrics.rttCurrent, metrics.rttSamplesCount)}`}>
                {metrics.rttSamplesCount > 0 ? metrics.rttCurrent : '—'}
              </span>
              <span className="text-slate-500 text-[10px]">ms</span>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2 pt-1 border-t border-slate-800/50 text-[10px]">
            <div>
              <span className="text-slate-500 block text-[9px]">Avg RTT</span>
              <span className="text-slate-300 font-semibold">
                {metrics.rttSamplesCount > 0 ? `${metrics.rttAvg} ms` : '—'}
              </span>
            </div>
            <div>
              <span className="text-slate-500 block text-[9px]">p95 RTT</span>
              <span className="text-cyan-400 font-semibold">
                {metrics.rttSamplesCount > 0 ? `${metrics.rttP95} ms` : '—'}
              </span>
            </div>
            <div>
              <span className="text-slate-500 block text-[9px]">Jitter</span>
              <span className="text-amber-300 font-semibold">
                {metrics.rttSamplesCount > 1 ? `${metrics.jitter} ms` : '—'}
              </span>
            </div>
          </div>
        </div>

        {/* 2. TRAFFIC BLOCK */}
        <div className="bg-slate-900/60 p-2.5 rounded-xl border border-slate-800/80 space-y-1.5">
          <div className="flex justify-between items-center text-[10px] text-slate-400 font-semibold uppercase tracking-wider">
            <span>Traffic</span>
            <button
              onClick={() => setShowDetails((prev) => !prev)}
              className="text-[9px] text-cyan-400/80 hover:text-cyan-300 underline"
            >
              {showDetails ? 'hide totals' : 'show totals'}
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2 text-[10px]">
            <div className="space-y-0.5 bg-slate-950/40 p-1.5 rounded-lg border border-slate-800/40">
              <div className="flex justify-between items-center">
                <span className="text-slate-400 text-[9px]">↑ TX Rate</span>
                <span className="text-cyan-300 font-bold">{metrics.txMsgRate} msg/s</span>
              </div>
              <div className="flex justify-between items-center text-slate-400">
                <span className="text-[9px]">Bandwidth</span>
                <span className="text-slate-200">{formatBytes(metrics.txBandwidthBytesPerSec)}/s</span>
              </div>
              {showDetails && (
                <div className="pt-1 border-t border-slate-800/40 flex justify-between text-slate-500 text-[9px]">
                  <span>Total TX</span>
                  <span>{formatBytes(metrics.txBytes)} ({metrics.txMessages})</span>
                </div>
              )}
            </div>

            <div className="space-y-0.5 bg-slate-950/40 p-1.5 rounded-lg border border-slate-800/40">
              <div className="flex justify-between items-center">
                <span className="text-slate-400 text-[9px]">↓ RX Rate</span>
                <span className="text-emerald-300 font-bold">{metrics.rxMsgRate} msg/s</span>
              </div>
              <div className="flex justify-between items-center text-slate-400">
                <span className="text-[9px]">Bandwidth</span>
                <span className="text-slate-200">{formatBytes(metrics.rxBandwidthBytesPerSec)}/s</span>
              </div>
              {showDetails && (
                <div className="pt-1 border-t border-slate-800/40 flex justify-between text-slate-500 text-[9px]">
                  <span>Total RX</span>
                  <span>{formatBytes(metrics.rxBytes)} ({metrics.rxMessages})</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 3. DRAWING STREAM BLOCK */}
        <div className="bg-slate-900/60 p-2.5 rounded-xl border border-slate-800/80 space-y-1.5">
          <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">
            Drawing Stream
          </div>
          <div className="grid grid-cols-2 gap-2 text-[10px]">
            <div className="space-y-0.5">
              <div className="flex justify-between text-slate-400">
                <span className="text-[9px]">Batches/s</span>
                <span className="text-cyan-300 font-bold">{metrics.drawBatchesPerSec} /s</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span className="text-[9px]">Batches Sent</span>
                <span className="text-slate-300">{metrics.drawBatchesSent}</span>
              </div>
            </div>
            <div className="space-y-0.5">
              <div className="flex justify-between text-slate-400">
                <span className="text-[9px]">Avg Pts/Batch</span>
                <span className="text-slate-200 font-semibold">{metrics.avgPointsPerBatch}</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span className="text-[9px]">Batches Recv</span>
                <span className="text-slate-300">{metrics.drawBatchesReceived}</span>
              </div>
            </div>
          </div>
          <div className="flex justify-between items-center pt-1 border-t border-slate-800/50 text-[10px]">
            <span className="text-slate-400 text-[9px]">Sequence Gaps</span>
            <span
              className={`font-semibold px-1.5 py-0.2 rounded text-[10px] ${
                metrics.sequenceGapCount === 0
                  ? 'bg-emerald-500/10 text-emerald-400'
                  : 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
              }`}
            >
              {metrics.sequenceGapCount}
            </span>
          </div>
        </div>

        {/* 4. CONNECTION BLOCK */}
        <div className="bg-slate-900/60 p-2.5 rounded-xl border border-slate-800/80 space-y-1 text-[10px]">
          <div className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider mb-1">
            Connection & Reliability
          </div>
          <div className="grid grid-cols-3 gap-1 text-[10px]">
            <div className="bg-slate-950/40 p-1.5 rounded-lg border border-slate-800/40">
              <span className="text-slate-500 block text-[9px]">Queue</span>
              <span className={`font-semibold ${metrics.gatewayQueueSize > 10 ? 'text-amber-400' : 'text-slate-200'}`}>
                {metrics.gatewayQueueSize}
              </span>
            </div>
            <div className="bg-slate-950/40 p-1.5 rounded-lg border border-slate-800/40">
              <span className="text-slate-500 block text-[9px]">Reconnects</span>
              <span className={`font-semibold ${metrics.reconnectCount > 0 ? 'text-amber-400' : 'text-slate-200'}`}>
                {metrics.reconnectCount}
              </span>
            </div>
            <div className="bg-slate-950/40 p-1.5 rounded-lg border border-slate-800/40">
              <span className="text-slate-500 block text-[9px]">Missed HB</span>
              <span className={`font-semibold ${metrics.heartbeatTimeoutCount > 0 ? 'text-rose-400' : 'text-slate-200'}`}>
                {metrics.heartbeatTimeoutCount}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Footer Hotkey Guide */}
      <div className="px-3 py-1.5 bg-slate-950 border-t border-slate-800/80 text-[9px] text-slate-500 flex justify-between items-center">
        <span>Toggle: <kbd className="px-1 py-0.5 rounded bg-slate-800 text-slate-300">Ctrl+Shift+N</kbd> or <kbd className="px-1 py-0.5 rounded bg-slate-800 text-slate-300">`</kbd></span>
        <span>Close: <kbd className="px-1 py-0.5 rounded bg-slate-800 text-slate-300">Esc</kbd></span>
      </div>
    </div>
  );
};
