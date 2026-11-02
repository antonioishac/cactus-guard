import { BrowserRouter, Routes, Route } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import Dashboard from './pages/Dashboard'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AppLayout />}>
          <Route index element={<Dashboard />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
