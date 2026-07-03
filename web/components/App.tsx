"use client";

import { useEffect, useMemo, useState } from "react";
import { Building2, MoreVertical, Settings } from "lucide-react";
import { AppBar } from "./AppBar";
import { SignIn } from "./SignIn";
import { EmptyState } from "./EmptyState";
import { Home } from "./Home";
import { SettingsView } from "./SettingsView";
import { AddEntryDialog } from "./AddEntryDialog";
import { InstitutionDialog } from "./InstitutionDialog";
import { InstitutionSwitcher } from "./InstitutionSwitcher";
import { JoinDialog } from "./JoinDialog";
import { InstitutionActionsSheet } from "./InstitutionActionsSheet";
import { ManageMembersView } from "./ManageMembersView";
import { CategoriesView } from "./CategoriesView";
import { PeriodExportView } from "./PeriodExportView";
import { ConfirmDialog } from "./ConfirmDialog";
import { useAuth } from "@/hooks/useAuth";
import { useMemberships } from "@/hooks/useMemberships";
import { useInstitutionDetail } from "@/hooks/useInstitutionDetail";
import {
  createInstitution,
  deleteEntry,
  deleteInstitution,
  joinInstitution,
  joinLink,
  removeMember,
  resolveCode,
  saveEntry,
  type EntryDraft,
} from "@/lib/firestore";
import { firebaseReady } from "@/lib/firebase";
import { analytics } from "@/lib/analytics";
import { APP_NAME, APP_TAGLINE } from "@/lib/constants";
import { haptics } from "@/lib/haptics";
import type { Entry } from "@/lib/types";

type View = "home" | "settings" | "members" | "categories" | "export";

function Splash() {
  return (
    <main className="grid min-h-dvh place-items-center">
      <p className="text-2xl font-extrabold tracking-tight opacity-60">{APP_NAME}</p>
    </main>
  );
}

export function App({ initialJoinCode }: { initialJoinCode?: string }) {
  const auth = useAuth();
  const uid = auth.user?.uid ?? null;
  const memberships = useMemberships(uid);
  const detail = useInstitutionDetail(memberships.selected?.institutionId ?? null);

  const [view, setView] = useState<View>("home");
  const [createOpen, setCreateOpen] = useState(false);
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  const [actionsOpen, setActionsOpen] = useState(false);
  const [entryDialogOpen, setEntryDialogOpen] = useState<Entry | "new" | null>(null);
  const [pendingJoinCode, setPendingJoinCode] = useState<string | undefined>(initialJoinCode);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (pendingJoinCode && auth.user) {
      setJoinOpen(true);
    }
  }, [pendingJoinCode, auth.user]);

  const profile = useMemo(
    () => ({
      displayName: auth.user?.displayName ?? "",
      email: auth.user?.email ?? "",
      photoUrl: auth.user?.photoURL ?? "",
    }),
    [auth.user],
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
          onSignOut={auth.signOut}
          onDeleteAccount={auth.deleteAccount}
        />
      </div>
    );
  }

  if (view === "members" && detail.institution) {
    return (
      <div className="mx-auto max-w-md">
        <ManageMembersView institution={detail.institution} myUid={uid!} onBack={() => setView("home")} />
      </div>
    );
  }

  if (view === "categories" && detail.institution) {
    return (
      <div className="mx-auto max-w-md">
        <CategoriesView institution={detail.institution} onBack={() => setView("home")} />
      </div>
    );
  }

  if (view === "export" && detail.institution) {
    return (
      <div className="mx-auto max-w-md">
        <PeriodExportView institution={detail.institution} onBack={() => setView("home")} />
      </div>
    );
  }

  // ── Home ──
  const noInstitutions = !memberships.loading && memberships.memberships.length === 0;
  const selected = memberships.selected;
  const canEdit = selected ? selected.role === "owner" || selected.role === "admin" : false;

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
          selected && (
            <button
              aria-label="Institution actions"
              onClick={() => setActionsOpen(true)}
              className="rounded-full p-2 transition hover:bg-black/5 dark:hover:bg-white/10"
            >
              <MoreVertical size={22} />
            </button>
          )
        }
      />

      {noInstitutions ? (
        <div className="mt-6">
          <EmptyState
            icon={<Building2 size={28} className="text-on-surface-variant" />}
            title="Add your first institution"
            description="Create a ledger for a temple, church, library, or any organisation you manage — or join one with a code."
            actionLabel="Create institution"
            onAction={() => {
              setCreateOpen(true);
              analytics.institutionAddOpen();
            }}
          />
          <button
            onClick={() => setJoinOpen(true)}
            className="mx-auto mt-3 block text-sm font-semibold text-primary"
          >
            Join with a code instead
          </button>
        </div>
      ) : selected && detail.institution ? (
        <>
          <button
            onClick={() => {
              setSwitcherOpen(true);
              analytics.institutionSwitcherOpen();
            }}
            className="mb-4 flex w-full items-center gap-3 rounded-2xl bg-surface-2 px-4 py-3 text-left transition hover:opacity-90"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate font-bold">{selected.institutionName}</p>
            </div>
            <span className="shrink-0 rounded-full bg-secondary-container px-3 py-1 text-xs font-semibold text-on-secondary-container">
              {selected.role === "owner" ? "Owner" : selected.role === "admin" ? "Admin" : "Member"}
            </span>
          </button>

          <Home
            institution={detail.institution}
            entries={detail.entries}
            canEdit={canEdit}
            onAdd={() => {
              analytics.entryAddOpen();
              setEntryDialogOpen("new");
            }}
            onEdit={(e) => {
              analytics.entryEditOpen();
              setEntryDialogOpen(e);
            }}
          />
        </>
      ) : (
        <Splash />
      )}

      {/* Dialogs */}
      {entryDialogOpen && detail.institution && (
        <AddEntryDialog
          open
          onClose={() => setEntryDialogOpen(null)}
          institution={detail.institution}
          categories={detail.categories}
          entry={entryDialogOpen === "new" ? null : entryDialogOpen}
          onSave={async (draft: EntryDraft) => {
            await saveEntry(uid!, detail.institution!.id, draft);
            analytics.entrySave(!draft.id, draft.type);
          }}
          onDelete={async (e) => {
            await deleteEntry(detail.institution!.id, e.id);
            analytics.entryDelete();
          }}
        />
      )}

      <InstitutionDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreate={async (input) => {
          const inst = await createInstitution(uid!, profile, input);
          memberships.setSelectedId(inst.id);
          analytics.institutionSave();
        }}
      />

      <InstitutionSwitcher
        open={switcherOpen}
        onClose={() => setSwitcherOpen(false)}
        memberships={memberships.memberships}
        selectedId={memberships.selectedId}
        onSelect={(id) => {
          memberships.setSelectedId(id);
          analytics.institutionSwitch();
        }}
        onCreate={() => {
          setCreateOpen(true);
          analytics.institutionAddOpen();
        }}
        onJoin={() => {
          setJoinOpen(true);
          analytics.institutionJoinOpen();
        }}
      />

      <JoinDialog
        open={joinOpen}
        onClose={() => {
          setJoinOpen(false);
          setPendingJoinCode(undefined);
        }}
        initialCode={pendingJoinCode}
        onResolve={resolveCode}
        onJoin={async (preview) => {
          const inst = await joinInstitution(uid!, profile, preview);
          memberships.setSelectedId(inst.id);
          analytics.institutionJoin();
        }}
      />

      {selected && (
        <InstitutionActionsSheet
          open={actionsOpen}
          onClose={() => setActionsOpen(false)}
          institutionName={selected.institutionName}
          role={selected.role}
          onShare={async () => {
            if (!detail.institution) return;
            const text = `Join "${detail.institution.name}" on Muthal\n\nCode: ${detail.institution.code}\n${joinLink(detail.institution.code)}`;
            analytics.institutionShare();
            if (navigator.share) {
              try {
                await navigator.share({ text });
                return;
              } catch {
                // fall through to clipboard
              }
            }
            await navigator.clipboard.writeText(text);
            haptics.success();
          }}
          onMembers={() => setView("members")}
          onCategories={() => setView("categories")}
          onExport={() => {
            setView("export");
            analytics.exportOpen();
          }}
          onLeave={async () => {
            if (!selected || !uid) return;
            await removeMember(selected.institutionId, uid);
            analytics.institutionLeave();
          }}
          onDelete={() => setConfirmDeleteOpen(true)}
        />
      )}

      <ConfirmDialog
        open={confirmDeleteOpen}
        title="Delete institution?"
        message={`Permanently delete "${selected?.institutionName}"? This removes every entry, category, and member. This cannot be undone.`}
        confirmLabel="Delete"
        danger
        busy={deleting}
        onClose={() => setConfirmDeleteOpen(false)}
        onConfirm={async () => {
          if (!selected || !uid) return;
          setDeleting(true);
          try {
            await deleteInstitution(uid, selected.institutionId);
            analytics.institutionDelete();
            setConfirmDeleteOpen(false);
          } finally {
            setDeleting(false);
          }
        }}
      />
    </div>
  );
}
