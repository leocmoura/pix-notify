import 'dotenv/config';
import pg from 'pg';
import { BufferJSON, initAuthCreds, proto } from '@whiskeysockets/baileys';

const { Pool } = pg;

export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_SSL === 'false' ? false : { rejectUnauthorized: false },
});

export async function initDb() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS auth_state (
      session_id TEXT NOT NULL,
      key        TEXT NOT NULL,
      value      TEXT NOT NULL,
      PRIMARY KEY (session_id, key)
    )
  `);
}

export async function useDbAuthState(sessionId = 'default') {
  const write = async (key, data) => {
    await pool.query(
      `INSERT INTO auth_state (session_id, key, value) VALUES ($1, $2, $3)
       ON CONFLICT (session_id, key) DO UPDATE SET value = EXCLUDED.value`,
      [sessionId, key, JSON.stringify(data, BufferJSON.replacer)]
    );
  };

  const read = async key => {
    const { rows } = await pool.query(
      'SELECT value FROM auth_state WHERE session_id = $1 AND key = $2',
      [sessionId, key]
    );
    return rows.length ? JSON.parse(rows[0].value, BufferJSON.reviver) : null;
  };

  const remove = async key => {
    await pool.query(
      'DELETE FROM auth_state WHERE session_id = $1 AND key = $2',
      [sessionId, key]
    );
  };

  const creds = (await read('creds')) || initAuthCreds();

  return {
    state: {
      creds,
      keys: {
        get: async (type, ids) => {
          const data = {};
          await Promise.all(
            ids.map(async id => {
              let value = await read(`${type}-${id}`);
              if (type === 'app-state-sync-key' && value) {
                value = proto.Message.AppStateSyncKeyData.fromObject(value);
              }
              data[id] = value;
            })
          );
          return data;
        },
        set: async data => {
          const tasks = [];
          for (const category in data) {
            for (const id in data[category]) {
              const value = data[category][id];
              const key = `${category}-${id}`;
              tasks.push(value ? write(key, value) : remove(key));
            }
          }
          await Promise.all(tasks);
        },
      },
    },
    saveCreds: () => write('creds', creds),
    clear: async () => {
      await pool.query('DELETE FROM auth_state WHERE session_id = $1', [sessionId]);
    },
  };
}