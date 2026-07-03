import { App } from "@/components/App";

/** Universal join link target (SPEC §2): https://<web-host>/join/{code}. Renders
 * the same app shell, prompting sign-in + a join confirmation for the code. */
export default async function JoinPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return <App initialJoinCode={code.toUpperCase()} />;
}
