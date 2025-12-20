// src/tokenStore.js
let accessToken = null;

export const tokenStore = {
    get() {
        return accessToken;
    },
    set(token) {
        accessToken = token;
    },
    clear() {
        accessToken = null;
    },
};
