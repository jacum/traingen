import React, { useEffect, useState } from "react";
  import fetcher from "./services/api";
  import Table from "./components/Table";

  const App: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
  const fetchData = async () => {
  try {
  const api = await fetcher(); // Instantiate fetcher with spec
  const response = await api.getData(); // Replace with actual endpoint
  setData(response);
  } catch (error) {
  console.error("Error fetching data", error);
  } finally {
  setLoading(false);
  }
  };
  fetchData();
  }, []);

  if (loading) return <div className="text-center">Loading...</div>;

  return (
<div className="container mx-auto mt-5">
<h1 className="text-2xl font-bold mb-4">Data Table</h1>
<Table data={data} />
</div>
  );
  };

  export default App;