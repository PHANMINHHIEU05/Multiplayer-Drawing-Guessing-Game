import React, { useEffect, useState } from 'react';
import { useMetricsStore, metricsStore, DrawingProtocolMode } from '../store/metricsStore';

export const NetworkInspector: React.FC = () => {
  const metrics = useMetricsStore((s) => s);
  const [isMinimized, setIsMinimized] = useState<boolean>(false);
  const [isVisible, setIsVisible] = useState<boolean>(true);

  // Global hotkey: Ctrl+Shift+N or ` (backtick) to toggle inspector
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'n') || e.key === '`') {
        e.preventDefault();
        setIsVisible((prev) => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  if (!isVisible) {
    return (
      <button
        onClick={() => setIsVisible(true)}
        className="fixed bottom-3 right-3 z-50 px-3 py-1.5 rounded-xl bg-slate-900/80 backdrop-blur-md border border-cyan-500/40 text-cyan-400 text-xs font-mono font-bold shadow-xl hover:bg-slate-800 transition-all flex items-center gap-1.5"
        title="Open Network Inspector (Ctrl+Shift+N or `)"
      >
        <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
        📊 NET INSPECTOR [{metrics.rttCurrent}ms]
      </button>
    );
  }

  const formatBytes = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'CONNECTED':
        return 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40';
      case 'CONNECTING':
      case 'RECONNECTING':
        return 'bg-amber-500/20 text-amber-400 border-amber-500/40 animate-pulse';
      default:
        return 'bg-rose-500/20 text-rose-400 border-rose-500/40';
    }
  };

  return (
    <div className="fixed bottom-3 right-3 z-50 w-80 sm:w-96 rounded-2xl bg-slate-950/90 backdrop-blur-xl border border-slate-700/60 shadow-2xl text-slate-200 font-mono text-xs overflow-hidden select-none animate-fadeIn transition-all">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 bg-slate-900/90 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <span className="text-cyan-400 font-bold tracking-wider">⚡ NETWORK INSPECTOR</span>
          <span className={`px-1.5 py-0.5 rounded-md border text-[10px] font-bold ${getStatusColor(metrics.status)}`}>
            {metrics.status}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => metricsStore.reset()}
            className="px-1.5 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 text-[10px] transition"
            title="Reset telemetry counters"
          >
            Reset
          </button>
          <button
            onClick={() => setIsMinimized((prev) => !prev)}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 text-sm font-bold"
            title={isMinimized ? 'Expand' : 'Minimize'}
          >
            {isMinimized ? '▲' : '▼'}
          </button>
          <button
            onClick={() => setIsVisible(false)}
            className="w-5 h-5 flex items-center justify-center rounded hover:bg-slate-800 text-slate-400 hover:text-rose-400 text-sm font-bold"
            title="Close Inspector"
          >
            ✕
          </button>
        </div>
      </div>

      {!isMinimized && (
        <div className="p-3 space-y-3 max-h-[75vh] overflow-y-auto custom-scrollbar">
          {/* Protocol Mode Selector */}
          <div>
            <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">
              Active Drawing Protocol
            </div>
            <div className="grid grid-cols-3 gap-1 bg-slate-900/80 p-1 rounded-xl border border-slate-800">
              {(['BINARY_BATCH', 'JSON_BATCH', 'JSON_POINT'] as DrawingProtocolMode[]).map((mode) => (
                <button
                  key={mode}
                  onClick={() => metricsStore.setDrawingMode(mode)}
                  className={`py-1 px-1.5 rounded-lg text-[10px] font-bold transition-all ${
                    metrics.drawingMode === mode
                      ? 'bg-cyan-600 text-white shadow-md shadow-cyan-600/30'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                  }`}
                >
                  {mode.replace('_', ' ')}
                </button>
              ))}
            </div>
          </div>

          {/* RTT & Latency Telemetry */}
          <div className="bg-slate-900/50 p-2.5 rounded-xl border border-slate-800/80 space-y-1.5">
            <div className="flex justify-between items-center text-[11px]">
              <span className="text-slate-400">RTT (Current):</span>
              <span className={`font-bold ${metrics.rttCurrent < 50 ? 'text-emerald-400' : metrics.rttCurrent < 150 ? 'text-amber-400' : 'text-rose-400'}`}>
                {metrics.rttCurrent} ms
              </span>
            </div>
            <div className="grid grid-cols-3 gap-2 pt-1 border-t border-slate-800/50 text-[10px]">
              <div>
                <span className="text-slate-500 block">Avg RTT</span>
                <span className="text-slate-300 font-bold">{metrics.rttAvg} ms</span>
              </div>
              <div>
                <span className="text-slate-500 block">p95 RTT</span>
                <span className="text-cyan-400 font-bold">{metrics.rttP95} ms</span>
              </div>
              <div>
                <span className="text-slate-500 block">Jitter</span>
                <span className="text-amber-300 font-bold">{metrics.jitter} ms</span>
              </div>
            </div>
          </div>

          {/* Throughput & Bandwidth */}
          <div className="bg-slate-900/50 p-2.5 rounded-xl border border-slate-800/80 space-y-1.5">
            <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">
              Throughput & Bandwidth
            </div>
            <div className="grid grid-cols-2 gap-2 text-[10px]">
              <div className="space-y-0.5">
                <div className="flex justify-between">
                  <span className="text-slate-500">TX Rate:</span>
                  <span className="text-cyan-300 font-bold">{metrics.txMsgRate} msg/s</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">TX Bandwidth:</span>
                  <span className="text-cyan-300">{formatBytes(metrics.txBandwidthBytesPerSec)}/s</span>
                </div>
                <div className="flex justify-between text-slate-500 text-[9px]">
                  <span>Total TX:</span>
                  <span>{formatBytes(metrics.txBytes)}</span>
                </div>
              </div>

              <div className="space-y-0.5">
                <div className="flex justify-between">
                  <span className="text-slate-500">RX Rate:</span>
                  <span className="text-emerald-300 font-bold">{metrics.rxMsgRate} msg/s</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">RX Bandwidth:</span>
                  <span className="text-emerald-300">{formatBytes(metrics.rxBandwidthBytesPerSec)}/s</span>
                </div>
                <div className="flex justify-between text-slate-500 text-[9px]">
                  <span>Total RX:</span>
                  <span>{formatBytes(metrics.rxBytes)}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Drawing Metrics */}
          <div className="bg-slate-900/50 p-2.5 rounded-xl border border-slate-800/80 space-y-1.5">
            <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">
              Drawing Stream
            </div>
            <div className="grid grid-cols-2 gap-2 text-[10px]">
              <div className="space-y-0.5">
                <div className="flex justify-between">
                  <span className="text-slate-500">Batches Sent:</span>
                  <span className="text-slate-300">{metrics.drawBatchesSent}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Points Sent:</span>
                  <span className="text-slate-300">{metrics.pointsSent}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Batches/s:</span>
                  <span className="text-cyan-400 font-bold">{metrics.drawBatchesPerSec}/s</span>
                </div>
              </div>

              <div className="space-y-0.5">
                <div className="flex justify-between">
                  <span className="text-slate-500">Batches Recv:</span>
                  <span className="text-slate-300">{metrics.drawBatchesReceived}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Points Recv:</span>
                  <span className="text-slate-300">{metrics.pointsReceived}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Avg Pts/Batch:</span>
                  <span className="text-slate-300 font-bold">{metrics.avgPointsPerBatch}</span>
                </div>
              </div>
            </div>

            <div className="flex justify-between items-center pt-1.5 border-t border-slate-800/50 text-[10px]">
              <span className="text-slate-400">Sequence Gaps:</span>
              <span className={`font-bold px-1.5 py-0.2 rounded ${metrics.sequenceGapCount === 0 ? 'text-emerald-400' : 'bg-amber-500/20 text-amber-400'}`}>
                {metrics.sequenceGapCount}
              </span>
            </div>
          </div>

          {/* Reliability & Infrastructure */}
          <div className="bg-slate-900/50 p-2.5 rounded-xl border border-slate-800/80 space-y-1 text-[10px]">
            <div className="flex justify-between">
              <span className="text-slate-500">Gateway Node:</span>
              <span className="text-slate-300 font-bold">{metrics.gatewayId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Gateway Queue:</span>
              <span className="text-slate-300">{metrics.gatewayQueueSize} frames</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Reconnect Count:</span>
              <span className="text-slate-300">{metrics.reconnectCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Missed Heartbeats:</span>
              <span className="text-slate-300">{metrics.heartbeatTimeoutCount}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
