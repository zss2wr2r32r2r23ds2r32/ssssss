export function PageHeader({ kicker, title, description }: { kicker: string; title: string; description: string }) {
  return (
    <header className="page-header">
      <div className="kicker">{kicker}</div>
      <h1>{title}</h1>
      <p>{description}</p>
    </header>
  );
}
