require('dotenv').config();
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode-terminal');
const express = require('express');

const { TOKEN, GRUPO_ID, PORT = 3000 } = process.env;

if (!TOKEN || !GRUPO_ID) {
  console.error('Defina TOKEN e GRUPO_ID no arquivo .env');
  process.exit(1);
}

process.on('unhandledRejection', err => {
  console.error('Erro não tratado (ignorado):', err && err.message ? err.message : err);
});

let pronto = false;

const client = new Client({
  authStrategy: new LocalAuth(),
  puppeteer: { args: ['--no-sandbox', '--disable-setuid-sandbox'] },
});

client.on('qr', qr => {
  console.log('Escaneie o QR code com o WhatsApp:');
  qrcode.generate(qr, { small: true });
});

client.on('authenticated', () => console.log('Autenticado'));

client.on('ready', () => {
  pronto = true;
  console.log('WhatsApp conectado!');
});

client.on('disconnected', reason => {
  pronto = false;
  console.error('WhatsApp desconectado:', reason);
});

client.on('auth_failure', msg => console.error('Falha na autenticação:', msg));

client.initialize();

const app = express();
app.use(express.json({ type: '*/*' }));

app.get('/saude', (req, res) => {
  res.json({ whatsapp: pronto });
});

app.post('/enviar', async (req, res) => {
  const tokenRecebido =
    (req.headers.authorization || '').replace('Bearer ', '') || req.query.token;
  if (tokenRecebido !== TOKEN) {
    return res.sendStatus(401);
  }

  
  if (!pronto) {
    return res.status(503).send('WhatsApp ainda não está pronto');
  }

  const { titulo = '', texto = '' } = req.body || {};
  if (!titulo && !texto) {
    return res.status(400).send('Envie titulo e/ou texto');
  }

  const mensagem = titulo ? `*${titulo}*\n${texto}` : texto;

  try {
    await client.sendMessage(GRUPO_ID, mensagem);
    console.log('Mensagem enviada:', mensagem.replace(/\n/g, ' | '));
    res.sendStatus(200);
  } catch (e) {
    console.error('Falha ao enviar:', e.message);
    res.status(500).send('Falha ao enviar a mensagem');
  }
});

app.listen(PORT, () => console.log(`API na porta ${PORT}`));