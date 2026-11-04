import 'dotenv/config';
import express from 'express';
import pino from 'pino';
import QRCode from 'qrcode';
import qrcodeTerminal from 'qrcode-terminal';
import makeWASocket, { Browsers, DisconnectReason } from '@whiskeysockets/baileys';
import { initDb, useDbAuthState } from './auth-db.js';

const { TOKEN, GRUPO_ID, PORT = 3000 } = process.env;

if (!TOKEN || !GRUPO_ID) {
  console.error('Defina TOKEN e GRUPO_ID no arquivo .env');
  process.exit(1);
}

process.on('unhandledRejection', err => {
  console.error('Erro não tratado (ignorado):', err?.message ?? err);
});

const logger = pino({ level: 'warn' });

let sock = null;
let pronto = false;
let qrAtual = null;

async function conectar() {
  const { state, saveCreds, clear } = await useDbAuthState('default');

  sock = makeWASocket({
    auth: state,
    logger,
    browser: Browsers.ubuntu('pix-notify'),
    markOnlineOnConnect: false,
  });

  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async ({ connection, lastDisconnect, qr }) => {
    if (qr) {
      qrAtual = qr;
      console.log('Escaneie o QR code (ou abra /qr?token=SEU_TOKEN):');
      qrcodeTerminal.generate(qr, { small: true });
    }

    if (connection === 'open') {
      pronto = true;
      qrAtual = null;
      console.log('WhatsApp conectado!');
    }

    if (connection === 'close') {
      pronto = false;
      const codigo = lastDisconnect?.error?.output?.statusCode;

      if (codigo === DisconnectReason.loggedOut) {
        console.error('Sessão encerrada pelo WhatsApp. Limpando e gerando novo QR...');
        await clear();
      } else {
        console.error(`Conexão fechada (código ${codigo}). Reconectando...`);
      }

      setTimeout(conectar, 3000);
    }
  });
}

const app = express();
app.use(express.json({ type: '*/*' }));

function autorizado(req) {
  const recebido =
    (req.headers.authorization || '').replace('Bearer ', '') || req.query.token;
  return recebido === TOKEN;
}

app.get('/saude', (req, res) => {
  res.json({ whatsapp: pronto });
});

app.get('/qr', async (req, res) => {
  if (!autorizado(req)) return res.sendStatus(401);
  if (pronto) return res.send('WhatsApp já está conectado.');
  if (!qrAtual) return res.send('Aguardando QR... a página recarrega sozinha.<meta http-equiv="refresh" content="3">');

  const imagem = await QRCode.toDataURL(qrAtual);
  res.send(`<meta http-equiv="refresh" content="15"><img src="${imagem}" width="320">`);
});

app.post('/enviar', async (req, res) => {
  if (!autorizado(req)) return res.sendStatus(401);
  if (!pronto) return res.status(503).send('WhatsApp ainda não está pronto');

  const { titulo = '', texto = '' } = req.body || {};
  if (!titulo && !texto) {
    return res.status(400).send('Envie titulo e/ou texto');
  }

  const mensagem = titulo ? `*${titulo}*\n${texto}` : texto;

  try {
    await sock.sendMessage(GRUPO_ID, { text: mensagem });
    console.log('Mensagem enviada');
    res.sendStatus(200);
  } catch (e) {
    console.error('Falha ao enviar:', e.message);
    res.status(500).send('Falha ao enviar a mensagem');
  }
});

await initDb();
await conectar();
app.listen(PORT, () => console.log(`API na porta ${PORT}`));