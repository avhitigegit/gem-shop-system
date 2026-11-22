import Link from "next/link";

export default function NotFound() {
  return (
    <main className="content" style={{ margin: "0 auto" }}>
      <div className="card access-denied">
        <div className="card-content" style={{ padding: 32 }}>
          <div style={{ fontSize: 40 }}>🔎</div>
          <h2>Page not found</h2>
          <p className="muted mt-s">This page does not exist. Use the menu to find the screen you need.</p>
          <Link className="btn btn-outline mt" href="/">
            Back to Home
          </Link>
        </div>
      </div>
    </main>
  );
}
