"use client";

import { useMemo, useState } from "react";
import { Building2, History, Plus, Settings } from "lucide-react";
import { AppBar } from "./AppBar";
import { SignIn } from "./SignIn";
import { EmptyState } from "./EmptyState";
import { Select } from "./controls";
import { Home } from "./Home";
import { HistoryView } from "./HistoryView";
import { SettingsView } from "./SettingsView";
import { AddEntryDialog } from "./AddEntryDialog";
import { InstitutionDialog } from "./InstitutionDialog";
import { useAuth } from "@/hooks/useAuth";
import { useInstitutions } from "@/hooks/useInstitutions";
import { useEntries } from "@/hooks/useEntries";
import { saveEntry, deleteEntry, type EntryDraft } from "@/lib/firestore";
import { firebaseReady } from "@/lib/firebase";
import { analytics } from "@/lib/analytics";
import { APP_NAME, APP_TAGLINE } from "@/lib/constants";
import type { Entry, Institution } from "@/lib/types";

type View = "home" | "history" | "settings";

function Splash() {
  return (
    <main className="grid min-h-dvh place-items-center">
      <p className="text-2xl font-extrabold tracking-tight opacity-60">{APP_NAME}</p>
    </main>
  );
}

export function App() {
  const auth = useAuth();
  const uid = auth.user?.uid ?? null;
  const insts = useInstitutions(uid);
  const { entries } = useEntries(uid);

  const [view, setView] = useState<View>("home");
  const [createOpen, setCreateOpen] = useState(false);
  const [entryDialog, setEntryDialog] = useState<{
    entry: Entry | null;
    institution: Institution;
  } | null>(null);

  const institutionEntries = useMemo(
    () =>
      insts.selected
        ? entries.filter((e) => e.institutionId === insts.selected!.id)
        : [],
    [entries, insts.selected],
  );

  // ── Auth gates ──
  if (!auth.initialized) return <Splash />;

  if (!auth.user) {
    return (
      <SignIn
        appName={APP_NAME}
        tagline={APP_TAGLINE}
        iconSrc="/icon-512.png"
        onSignIn={auth.signInWithGoogle}
        externalError={
          !firebaseReady
            ? "Add the web Firebase config (web/.env.local) to enable sign-in."
            : auth.authError
        }
      />
    );
  }

  // ── Secondary screens ──
  if (view === "settings") {
    return (
      <div className="mx-auto max-w-md">
        <SettingsView
          onBack={() => setView("home")}
          email={auth.user.email}
          institutions={insts.institutions}
          onDeleteInstitution={insts.remove}
          onSignOut={auth.signOut}
          onDeleteAccount={auth.deleteAccount}
        />
      </div>
    );
  }

  if (view === "history") {
    return (
      <div className="mx-auto max-w-md">
        <HistoryView
          entries={entries}
          onBack={() => setView("home")}
          onEdit={(e) => {
            const inst =
              insts.institutions.find((i) => i.id === e.institutionId) ??
              insts.selected;
            if (inst) {
              analytics.entryEditOpen();
              setEntryDialog({ entry: e, institution: inst });
            }
          }}
        />
      </div>
    );
  }

  // ── Home ──
  const noInstitutions = !insts.loading && insts.institutions.length === 0;

  return (
    <div className="mx-auto max-w-md px-4">
      <AppBar
        title={APP_NAME}
        leading={
          <button
            aria-label="Settings"
            onClick={() => {
              setView("settings");
              analytics.screenSettingsOpen();
            }}
            className="rounded-full p-2 transition hover:bg-black/5 dark:hover:bg-white/10"
          >
            <Settings size={22} />
          </button>
        }
        actions={
          <button
            aria-label="All entries"
            onClick={() => {
              setView("history");
              analytics.screenHistoryOpen();
            }}
            className="rounded-full p-2 transition hover:bg-black/5 dark:hover:bg-white/10"
          >
            <History size={22} />
          </button>
        }
      />

      {noInstitutions ? (
        <div className="mt-6">
          <EmptyState
            icon={<Building2 size={28} className="text-on-surface-variant" />}
            title="Add your first institution"
            description="Create a ledger for a temple, church, library, or any organisation you manage."
            actionLabel="Create institution"
            onAction={() => {
              setCreateOpen(true);
              analytics.institutionAddOpen();
            }}
          />
        </div>
      ) : insts.selected ? (
        <>
          {/* Institution switcher */}
          <div className="mb-4 flex items-center gap-2">
            <Select
              className="flex-1"
              value={insts.selected.id}
              onChange={(e) => {
                insts.setSelectedId(e.target.value);
                analytics.institutionSwitch();
              }}
            >
              {insts.institutions.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.name}
                </option>
              ))}
            </Select>
            <button
              aria-label="New institution"
              onClick={() => {
                setCreateOpen(true);
                analytics.institutionAddOpen();
              }}
              className="grid h-12 w-12 shrink-0 place-items-center rounded-lg border border-outline-strong text-primary transition hover:bg-primary/10"
            >
              <Plus size={20} />
            </button>
          </div>

          <Home
            institution={insts.selected}
            entries={institutionEntries}
            onAdd={() => {
              analytics.entryAddOpen();
              setEntryDialog({ entry: null, institution: insts.selected! });
            }}
            onEdit={(e) => {
              analytics.entryEditOpen();
              setEntryDialog({ entry: e, institution: insts.selected! });
            }}
          />
        </>
      ) : (
        <Splash />
      )}

      {/* Dialogs */}
      {entryDialog && (
        <AddEntryDialog
          open
          onClose={() => setEntryDialog(null)}
          institution={entryDialog.institution}
          entry={entryDialog.entry}
          onSave={async (draft: EntryDraft) => {
            await saveEntry(auth.user!.uid, entryDialog.institution, draft);
            analytics.entrySave(!draft.id, draft.type);
          }}
          onDelete={async (e) => {
            await deleteEntry(auth.user!.uid, e.institutionId, e.id);
            analytics.entryDelete();
          }}
        />
      )}

      <InstitutionDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreate={async (input) => {
          const id = await insts.add(input);
          insts.setSelectedId(id);
          analytics.institutionSave();
        }}
      />
    </div>
  );
}
