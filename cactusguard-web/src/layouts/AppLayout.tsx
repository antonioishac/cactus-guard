import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Menu, Bell, ChevronDown } from 'lucide-react'
import Sidebar from '../components/Sidebar'

export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(true)

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Sidebar */}
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main */}
      <div className={`${sidebarOpen ? 'lg:ml-64' : ''} transition-all`}>
        {/* TopBar */}
        <header className="bg-white border-b border-gray-200 px-4 py-3 flex items-center justify-between">
          <button onClick={() => setSidebarOpen(!sidebarOpen)} className="p-2 hover:bg-gray-100 rounded-lg">
            <Menu size={20} />
          </button>
          <div className="flex items-center gap-4">
            <button className="relative p-2 hover:bg-gray-100 rounded-lg">
              <Bell size={20} />
              <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full" />
            </button>
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 bg-accent rounded-full flex items-center justify-center text-white text-sm font-medium">AM</div>
              <span className="text-sm font-medium">Ana Martins</span>
              <ChevronDown size={16} className="text-gray-400" />
            </div>
          </div>
        </header>

        {/* Content */}
        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
