import { MongoClient } from 'mongodb';
const uri = process.env.MONGODB_URI;
if (!uri) throw new Error('MONGODB_URI is missing');
const globalForMongo = globalThis as unknown as { mongo?: MongoClient };
export const client = globalForMongo.mongo ?? new MongoClient(uri);
if (process.env.NODE_ENV !== 'production') globalForMongo.mongo = client;
export async function db() { await client.connect(); return client.db('family_guard'); }
