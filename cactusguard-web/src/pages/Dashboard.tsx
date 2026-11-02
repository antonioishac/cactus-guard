import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import { AlertTriangle, FolderOpen, Search, UserCheck } from 'lucide-react'

const metrics = [
  { label: 'Alertas Abertos', value: 47, change: '+12%', positive: true, icon: AlertTriangle, color: 'text-red-500', bg: 'bg-red-50' },
  { label: 'Casos Pendentes', value: 23, change: '+8%', positive: true, icon: FolderOpen, color: 'text-amber-500', bg: 'bg-amber-50' },
  { label: 'Screenings', value: '1.284', change: '+15%', positive: true, icon: Search, color: 'text-accent', bg: 'bg-teal-50' },
  { label: 'KYC Pendentes', value: 156, change: '+5%', positive: true, icon: UserCheck, color: 'text-gray-600', bg: 'bg-gray-100' },
]

const chartData = [
  { day: '15 Mai', value: 25 },
  { day: '16 Mai', value: 32 },
  { day: '17 Mai', value: 40 },
  { day: '18 Mai', value: 52 },
  { day: '19 Mai', value: 45 },
  { day: '20 Mai', value: 38 },
  { day: '21 Mai', value: 35 },
]

const severityData = [
  { name: 'Alta', value: 18, color: '#dc2626' },
  { name: 'Média', value: 17, color: '#f59e0b' },
  { name: 'Baixa', value: 12, color: '#00c9a7' },
]

const alerts = [
  { id: 'ALT-2025-0478', name: 'Transação de Alto Valor', entity: 'João Silva & Cia.', severity: 'Alta', date: '21/05 10:24', status: 'Aberto' },
  { id: 'ALT-2025-0477', name: 'PEP Identificado', entity: 'Maria Oliveira', severity: 'Média', date: '21/05 09:15', status: 'Aberto' },
  { id: 'ALT-2025-0476', name: 'Lista de Sanções', entity: 'Global Trade Ltd.', severity: 'Alta', date: '20/05 16:42', status: 'Em Análise' },
  { id: 'ALT-2025-0475', name: 'Atividade Incomum', entity: 'Tech Solutions S.A.', severity: 'Baixa', date: '20/05 14:33', status: 'Aberto' },
  { id: 'ALT-2025-0474', name: 'Origem de Fundos', entity: 'Carlos Mendes', severity: 'Média', date: '20/05 11:07', status: 'Aberto' },
]

const severityColors: Record<string, string> = {
  Alta: 'bg-red-100 text-red-700',
  Média: 'bg-amber-100 text-amber-700',
  Baixa: 'bg-teal-100 text-teal-700',
}

const statusColors: Record<string, string> = {
  Aberto: 'bg-blue-100 text-blue-700',
  'Em Análise': 'bg-purple-100 text-purple-700',
}

export default function Dashboard() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">Dashboard</h1>
        <p className="text-sm text-gray-500 mt-1">Visão geral do sistema de conformidade AML/PLD.</p>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {metrics.map(m => (
          <div key={m.label} className="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
            <div className="flex items-center gap-3">
              <div className={`w-10 h-10 rounded-lg ${m.bg} flex items-center justify-center`}>
                <m.icon size={20} className={m.color} />
              </div>
              <span className="text-sm text-gray-500">{m.label}</span>
            </div>
            <div className="mt-3 flex items-end justify-between">
              <span className="text-2xl font-semibold">{m.value}</span>
              <span className={`text-xs ${m.positive ? 'text-green-600' : 'text-red-600'}`}>{m.change} vs. ontem</span>
            </div>
          </div>
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
          <h3 className="text-sm font-medium text-gray-700 mb-4">Alertas ao Longo do Tempo (7 dias)</h3>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={chartData}>
              <XAxis dataKey="day" tick={{ fontSize: 12 }} />
              <YAxis tick={{ fontSize: 12 }} />
              <Tooltip />
              <Line type="monotone" dataKey="value" stroke="#00c9a7" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div className="bg-white rounded-xl p-4 border border-gray-100 shadow-sm">
          <h3 className="text-sm font-medium text-gray-700 mb-4">Distribuição por Severidade</h3>
          <div className="flex items-center gap-6">
            <ResponsiveContainer width={140} height={140}>
              <PieChart>
                <Pie data={severityData} dataKey="value" innerRadius={40} outerRadius={60}>
                  {severityData.map((e, i) => <Cell key={i} fill={e.color} />)}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="space-y-2">
              {severityData.map(s => (
                <div key={s.name} className="flex items-center gap-2 text-sm">
                  <div className="w-3 h-3 rounded-full" style={{ background: s.color }} />
                  <span className="text-gray-600">{s.name}</span>
                  <span className="font-medium">{s.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 flex justify-between items-center">
          <h3 className="text-sm font-medium text-gray-700">Alertas Recentes</h3>
          <button className="text-sm text-accent hover:underline">Ver todos →</button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500">
              <tr>
                <th className="px-4 py-2 text-left font-medium">ID</th>
                <th className="px-4 py-2 text-left font-medium">Alerta</th>
                <th className="px-4 py-2 text-left font-medium">Entidade</th>
                <th className="px-4 py-2 text-left font-medium">Severidade</th>
                <th className="px-4 py-2 text-left font-medium">Data</th>
                <th className="px-4 py-2 text-left font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map(a => (
                <tr key={a.id} className="border-t border-gray-50 hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-500">{a.id}</td>
                  <td className="px-4 py-3">{a.name}</td>
                  <td className="px-4 py-3 text-gray-600">{a.entity}</td>
                  <td className="px-4 py-3"><span className={`px-2 py-0.5 rounded text-xs ${severityColors[a.severity]}`}>{a.severity}</span></td>
                  <td className="px-4 py-3 text-gray-500">{a.date}</td>
                  <td className="px-4 py-3"><span className={`px-2 py-0.5 rounded text-xs ${statusColors[a.status]}`}>{a.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
