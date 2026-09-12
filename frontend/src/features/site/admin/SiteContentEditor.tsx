import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import { ExternalLink, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useBlocker } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { siteContentApi } from "@/lib/api";
import { LEAVE_MESSAGE, setUnsavedChanges } from "@/lib/unsavedChanges";
import type { SiteContentFieldDto } from "@/lib/api";
import { fieldLabel, groupFields } from "./siteContentFields";

type EditorState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; fields: SiteContentFieldDto[]; saved: Record<string, string> };

const NO_VALUES: Record<string, string> = {};

const FIELD_CLASS =
  "mt-2 w-full rounded-2xl border-2 border-slate-100 bg-slate-50 px-4 py-3 text-sm text-slate-900 transition-colors focus:border-cyan-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-cyan-500/20";

/**
 * SiteContentEditor: Yöneticinin karşılama sitesinin yazılarını doldurduğu ekran (T-063C / S-026).
 *
 * - Alanlar ve azami uzunluklar sunucudan okunur; sınırın tek kaynağı sunucudur (APP-01 §2.1).
 * - Yalnızca değişen alanlar gönderilir; kayıttan sonra sunucunun temizlediği değer gösterilir.
 * - Kaydedilmemiş değişiklik varken sekme kapatma, yenileme, başka adrese geçiş (geri düğmesi dahil) ve çıkış
 *   onay ister; panelde sekme değiştirmek taslağı silmez (IST-02 §4.3).
 */
export function SiteContentEditor() {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<EditorState>({ status: "loading" });
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([siteContentApi.getFields(), siteContentApi.get()])
      .then(([fieldsResponse, contentResponse]) => {
        if (!active) return;
        const saved = contentResponse.data.data ?? {};
        setState({ status: "ready", fields: fieldsResponse.data.data ?? [], saved });
        setDraft(saved);
      })
      .catch(() => {
        if (active) setState({ status: "error" });
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  const saved = state.status === "ready" ? state.saved : NO_VALUES;
  const changedKeys = useMemo(
    () => Object.keys(draft).filter((key) => (draft[key] ?? "") !== (saved[key] ?? "")),
    [draft, saved],
  );
  const dirty = changedKeys.length > 0;
  const groups = useMemo(() => groupFields(state.status === "ready" ? state.fields : []), [state]);

  useEffect(() => {
    if (!dirty) return;
    const warnBeforeLeaving = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warnBeforeLeaving);
    return () => window.removeEventListener("beforeunload", warnBeforeLeaving);
  }, [dirty]);

  // Çıkış adres değiştirmeden paneli kapattığı için bayrağı okur (App.tsx → confirmLeave).
  useEffect(() => {
    setUnsavedChanges(dirty);
    return () => setUnsavedChanges(false);
  }, [dirty]);

  // Adres değişikliği (menü, logo, tarayıcının geri/ileri düğmesi) kaydedilmemiş değişiklikte onay ister.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => dirty && currentLocation.pathname !== nextLocation.pathname,
  );
  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm(LEAVE_MESSAGE)) blocker.proceed();
    else blocker.reset();
  }, [blocker]);

  const save = async () => {
    if (state.status !== "ready" || !dirty) return;
    setSaving(true);
    try {
      const values = Object.fromEntries(changedKeys.map((key) => [key, draft[key] ?? ""]));
      const response = await siteContentApi.update(values);
      const updated = response.data.data ?? {};
      setState({ ...state, saved: updated });
      setDraft(updated);
      toast.success("Site içeriği kaydedildi.");
    } catch (error) {
      const message = axios.isAxiosError(error) ? error.response?.data?.message : undefined;
      toast.error(message || "Site içeriği kaydedilemedi. Değişiklikleriniz duruyor, yeniden deneyin.");
    } finally {
      setSaving(false);
    }
  };

  if (state.status === "loading") {
    return (
      <p role="status" className="py-16 text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">
        Site içeriği yükleniyor…
      </p>
    );
  }

  if (state.status === "error") {
    return (
      <div role="alert" className="max-w-lg rounded-[2rem] border border-red-100 bg-white p-8">
        <p className="font-bold text-slate-900">Site içeriği yüklenemedi.</p>
        <p className="mt-2 text-sm text-slate-600">Bağlantınızı kontrol edip yeniden deneyin.</p>
        <Button
          type="button"
          className="mt-6 h-11"
          onClick={() => {
            setState({ status: "loading" });
            setAttempt((count) => count + 1);
          }}
        >
          Yeniden dene
        </Button>
      </div>
    );
  }

  return (
    <section aria-labelledby="site-icerigi-baslik" className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Karşılama sitesi</p>
          <h2 id="site-icerigi-baslik" className="mt-2 text-3xl font-black tracking-tighter text-slate-900">
            Site içeriği
          </h2>
          <p className="mt-2 max-w-xl text-sm text-slate-500">
            Boş bıraktığınız alanlar sitede görünmez. Yazılar düz metin olarak yayınlanır; satır sonları korunur.
          </p>
        </div>
        <a
          href="/karsilama"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex h-11 shrink-0 items-center gap-2 self-start rounded-2xl border-2 border-slate-100 bg-white px-5 text-[11px] font-black uppercase tracking-[0.2em] text-slate-700 hover:border-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2"
        >
          Siteyi görüntüle
          <ExternalLink className="h-4 w-4" aria-hidden="true" />
        </a>
      </header>

      {groups.map((group) => (
        <fieldset
          key={group.title}
          className="rounded-[2rem] border border-slate-100 bg-white p-6 shadow-xl shadow-slate-100/50 sm:p-8"
        >
          <legend className="px-2 text-[11px] font-black uppercase tracking-[0.3em] text-slate-900">{group.title}</legend>
          <div className="mt-2 space-y-6">
            {group.fields.map((field) => (
              <FieldInput
                key={field.key}
                field={field}
                value={draft[field.key] ?? ""}
                onChange={(value) => setDraft((current) => ({ ...current, [field.key]: value }))}
              />
            ))}
          </div>
        </fieldset>
      ))}

      <div className="sticky bottom-20 z-10 flex flex-col gap-3 rounded-[2rem] border border-slate-200 bg-white/95 p-4 shadow-2xl backdrop-blur sm:flex-row sm:items-center sm:justify-between lg:bottom-4">
        <p role="status" className="text-sm font-bold text-slate-600">
          {dirty ? `${changedKeys.length} alanda kaydedilmemiş değişiklik var.` : "Tüm değişiklikler kaydedildi."}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" className="h-11" disabled={!dirty || saving} onClick={() => setDraft(saved)}>
            Değişiklikleri geri al
          </Button>
          <Button type="button" className="h-11" disabled={!dirty || saving} onClick={save}>
            {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
            Kaydet
          </Button>
        </div>
      </div>
    </section>
  );
}

function FieldInput({
  field,
  value,
  onChange,
}: {
  field: SiteContentFieldDto;
  value: string;
  onChange: (value: string) => void;
}) {
  const id = `site-alani-${field.key.replace(/\./g, "-")}`;
  const counterId = `${id}-sayac`;

  return (
    <div>
      <div className="flex items-baseline justify-between gap-4">
        <label htmlFor={id} className="text-sm font-bold text-slate-800">
          {fieldLabel(field.key)}
        </label>
        <span id={counterId} className="shrink-0 text-[11px] font-bold tabular-nums text-slate-400">
          {value.length} / {field.maxLength}
        </span>
      </div>
      {field.maxLength > 150 ? (
        <textarea
          id={id}
          value={value}
          maxLength={field.maxLength}
          rows={field.maxLength > 2000 ? 10 : 4}
          aria-describedby={counterId}
          onChange={(event) => onChange(event.target.value)}
          className={`${FIELD_CLASS} resize-y`}
        />
      ) : (
        <input
          id={id}
          type="text"
          value={value}
          maxLength={field.maxLength}
          aria-describedby={counterId}
          onChange={(event) => onChange(event.target.value)}
          className={`${FIELD_CLASS} min-h-[44px]`}
        />
      )}
    </div>
  );
}
