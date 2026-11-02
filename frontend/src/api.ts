const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');

export type Pedido = {
  id: number;
  producto: string;
  cantidad: number;
  cliente: string;
  estado: string;
  fechaCreacion: string;
};

export type NuevoPedido = {
  producto: string;
  cantidad: number;
  cliente: string;
};

async function request<T>(path: string, token: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers ?? {}),
    },
  });

  if (!response.ok) {
    const detalle = await response.text();
    throw new Error(`${response.status} ${response.statusText}${detalle ? `: ${detalle}` : ''}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const api = {
  listar: (token: string) => request<Pedido[]>('/api/pedidos', token),
  crear: (token: string, pedido: NuevoPedido) =>
    request<Pedido>('/api/pedidos', token, {
      method: 'POST',
      body: JSON.stringify(pedido),
    }),
  cancelar: (token: string, id: number) =>
    request<Pedido>(`/api/pedidos/${id}/cancelar`, token, { method: 'POST' }),
  eliminar: (token: string, id: number) =>
    request<void>(`/api/pedidos/${id}`, token, { method: 'DELETE' }),
};
