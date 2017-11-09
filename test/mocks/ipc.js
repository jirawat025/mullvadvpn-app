// @flow
import type { IpcFacade, BackendState } from '../../app/lib/ipc-facade';

interface MockIpc {
  sendNewState: (BackendState) => void;
  killWebSocket: () => void;
  -getAccountData: *;
  -connect: *;
  -getAccount: *;
  -authenticate: *;
}

export function newMockIpc() {

  const stateListeners = [];
  const connectionCloseListeners = [];

  const mockIpc: IpcFacade & MockIpc = {

    setConnectionString: (_str: string) => {},

    getAccountData: (accountToken) => Promise.resolve({
      accountToken: accountToken,
      expiry: '',
    }),

    getAccount: () => Promise.resolve('1111'),

    setAccount: () => Promise.resolve(),

    updateRelayConstraints: () => Promise.resolve(),

    getRelayContraints: () => Promise.resolve({
      host: { only: 'www.example.com' },
      tunnel: { openvpn: {
        port: 'any',
        protocol: 'any',
      }},
    }),

    connect: () => Promise.resolve(),

    disconnect: () => Promise.resolve(),

    shutdown: () => Promise.resolve(),

    getIp: () => Promise.resolve('1.2.3.4'),

    getLocation: () => Promise.resolve({
      city: '',
      country: '',
      latlong: [0, 0],
    }),

    getState: () => Promise.resolve({
      state: 'unsecured',
      target_state:'unsecured',
    }),

    registerStateListener: (listener: (BackendState) => void) => {
      stateListeners.push(listener);
    },

    sendNewState: (state: BackendState) => {
      for(const l of stateListeners) {
        l(state);
      }
    },

    authenticate: (_secret: string) => Promise.resolve(),

    setCloseConnectionHandler: (listener: () => void) => {
      connectionCloseListeners.push(listener);
    },

    killWebSocket: () => {
      for(const l of connectionCloseListeners) {
        l();
      }
    }
  };

  return mockIpc;
}
