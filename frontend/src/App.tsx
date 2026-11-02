import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useAuth } from 'react-oidc-context';
import { api, type NuevoPedido, type Pedido } from './api';

function App() {
  const auth = useAuth();
  const token = auth.user?.access_token;

  const [pedidos, setPedidos] = useState<Pedido[]>([]);
  const [form, setForm] = useState<NuevoPedido>({ producto: '', cantidad: 1, cliente: '' });
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  const cargar = useCallback(async () => {
    if (!token) return;
    setCargando(true);
    setError(null);
    try {
      setPedidos(await api.listar(token));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  if (auth.isLoading) {
    return <div className="center">Cargando sesión…</div>;
  }

  if (auth.error) {
    return <div className="center error">Error de autenticación: {auth.error.message}</div>;
  }

  if (!auth.isAuthenticated) {
    return (
      <main className="login">
        <section className="card login-card">
          <span className="badge">AWS Cognito · OAuth 2.0 / OIDC</span>
          <h1>Pedidos360</h1>
          <p>
            Inicia sesión con tu usuario de Cognito. El frontend obtendrá un JWT y lo enviará al
            API Gateway para consumir el backend desplegado en EC2.
          </p>
          <button className="btn primary" onClick={() => void auth.signinRedirect()}>
            Iniciar sesión con Cognito
          </button>
        </section>
      </main>
    );
  }

  const profile = auth.user?.profile;
  const groups = (profile?.['cognito:groups'] as string[] | undefined) ?? [];

  const crear = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!token) return;
    setError(null);
    try {
      await api.crear(token, form);
      setForm({ producto: '', cantidad: 1, cliente: '' });
      await cargar();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const cancelar = async (id: number) => {
    if (!token) return;
    setError(null);
    try {
      await api.cancelar(token, id);
      await cargar();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const eliminar = async (id: number) => {
    if (!token) return;
    setError(null);
    try {
      await api.eliminar(token, id);
      await cargar();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <span className="badge">Pedidos360</span>
          <h1>Panel de pedidos</h1>
        </div>
        <div className="user-box">
          <div>
            <strong>{profile?.email ?? profile?.preferred_username ?? 'Usuario'}</strong>
            <small>{groups.length ? groups.join(' · ') : 'Sin grupos'}</small>
          </div>
          <button className="btn ghost" onClick={() => void auth.signoutRedirect()}>
            Cerrar sesión
          </button>
        </div>
      </header>

      <section className="card token-card">
        <div>
          <span className="label">Access token (JWT)</span>
          <code>{token ? `${token.slice(0, 42)}…` : '—'}</code>
        </div>
        <div>
          <span className="label">API Gateway</span>
          <code>{import.meta.env.VITE_API_URL || 'http://localhost:8080'}</code>
        </div>
      </section>

      {error && <div className="alert error">{error}</div>}

      <section className="card">
        <h2>Nuevo pedido</h2>
        <form className="grid-form" onSubmit={crear}>
          <label>
            Producto
            <input
              value={form.producto}
              onChange={(e) => setForm({ ...form, producto: e.target.value })}
              placeholder="Notebook, mouse, teclado…"
              required
            />
          </label>
          <label>
            Cantidad
            <input
              type="number"
              min={1}
              value={form.cantidad}
              onChange={(e) => setForm({ ...form, cantidad: Number(e.target.value) })}
              required
            />
          </label>
          <label>
            Cliente
            <input
              value={form.cliente}
              onChange={(e) => setForm({ ...form, cliente: e.target.value })}
              placeholder="Nombre del cliente"
              required
            />
          </label>
          <button className="btn primary" type="submit">
            Crear pedido
          </button>
        </form>
      </section>

      <section className="card">
        <div className="section-title">
          <h2>Pedidos registrados</h2>
          <button className="btn ghost" onClick={() => void cargar()} disabled={cargando}>
            {cargando ? 'Actualizando…' : 'Actualizar'}
          </button>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Producto</th>
                <th>Cantidad</th>
                <th>Cliente</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {pedidos.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty">
                    No hay pedidos todavía.
                  </td>
                </tr>
              )}
              {pedidos.map((pedido) => (
                <tr key={pedido.id}>
                  <td>{pedido.id}</td>
                  <td>{pedido.producto}</td>
                  <td>{pedido.cantidad}</td>
                  <td>{pedido.cliente}</td>
                  <td>
                    <span className={`status ${pedido.estado.toLowerCase()}`}>{pedido.estado}</span>
                  </td>
                  <td className="actions">
                    <button className="btn small" onClick={() => void cancelar(pedido.id)}>
                      Cancelar
                    </button>
                    <button className="btn small danger" onClick={() => void eliminar(pedido.id)}>
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <footer className="footer">
        <span>EC2 · Spring Boot · Cognito · API Gateway · RabbitMQ</span>
        <span>Evaluación Parcial N°2 · DSY1107</span>
      </footer>
    </div>
  );
}

export default App;
