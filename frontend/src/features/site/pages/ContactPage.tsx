import type { ReactNode } from "react";
import { EmptyPage, PageHeader, Prose, SiteContentGate } from "../components/SiteBlocks";
import { mailtoHref, telHref, text } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

const LINK_CLASS =
  "inline-flex min-h-[44px] items-center break-all rounded-lg text-cyan-700 underline decoration-cyan-300 underline-offset-4 hover:text-cyan-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2";

/**
 * ContactPage: Kurumun iletişim bilgileri (T-063B / S-026). Form yoktur — uygulamada mesajlaşma yok.
 * Telefon ve e-posta geçerli görünüyorsa bağlantı olur, değilse düz metin kalır.
 */
export default function ContactPage() {
  useDocumentTitle("İletişim");
  return (
    <>
      <PageHeader title="İletişim" />
      <SiteContentGate>
        {(content) => {
          const address = text(content, "contact.address");
          const phone = text(content, "contact.phone");
          const email = text(content, "contact.email");
          const hours = text(content, "contact.hours");
          const note = text(content, "contact.note");

          if (![address, phone, email, hours, note].some(Boolean)) {
            return <EmptyPage />;
          }

          const phoneHref = phone ? telHref(phone) : null;
          const emailHref = email ? mailtoHref(email) : null;

          return (
            <div className="mx-auto grid max-w-6xl gap-12 px-4 py-12 sm:px-8 lg:grid-cols-2">
              <dl className="space-y-8">
                {address && (
                  <ContactItem label="Adres">
                    <span className="whitespace-pre-line">{address}</span>
                  </ContactItem>
                )}
                {phone && (
                  <ContactItem label="Telefon">
                    {phoneHref ? <a href={phoneHref} className={LINK_CLASS}>{phone}</a> : phone}
                  </ContactItem>
                )}
                {email && (
                  <ContactItem label="E-posta">
                    {emailHref ? <a href={emailHref} className={LINK_CLASS}>{email}</a> : email}
                  </ContactItem>
                )}
                {hours && (
                  <ContactItem label="Çalışma saatleri">
                    <span className="whitespace-pre-line">{hours}</span>
                  </ContactItem>
                )}
              </dl>
              <Prose value={note} className="max-w-xl" />
            </div>
          );
        }}
      </SiteContentGate>
    </>
  );
}

function ContactItem({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] font-black uppercase tracking-[0.3em] text-slate-500">{label}</dt>
      <dd className="mt-2 break-words text-lg font-semibold text-slate-900">{children}</dd>
    </div>
  );
}
