import './App.css'
import './index.css'

import { BrowserRouter, Routes, Route, Link, Navigate } from 'react-router-dom'
import { paths } from './services/user-api.ts'
import createClient from "openapi-fetch";
import {
    QueryClient,
    QueryClientProvider,
    useQuery,
} from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'

const queryClient = new QueryClient()
const client = createClient<paths>({ baseUrl: "" });

export default function App() {
    return (
        <QueryClientProvider client={queryClient}>
            <ReactQueryDevtools />
            <BrowserRouter>
                <Routes>
                    <Route path="/" element={<HomePage />} />
                    <Route path="/combo" element={<Combo />} />
                    <Route path="/training" element={<Training />} />
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </BrowserRouter>
        </QueryClientProvider>
    )
}

function HomePage() {
    return (
        <div className="flex flex-col items-center p-8">
            <div className="flex flex-col gap-6 items-center mt-8">
                <Link to="/combo" className="px-8 py-4 bg-blue-500 text-white text-xl font-semibold rounded-lg hover:bg-blue-600 transition-colors w-64 text-center block">
                Combo
                </Link>
                <Link to="/training" className="px-8 py-4 bg-blue-500 text-white text-xl font-semibold rounded-lg hover:bg-blue-600 transition-colors w-64 text-center">
                Training
                </Link>
            </div>
        </div>
    )
}

function Combo() {
    const {isPending, error, data, isFetching} = useQuery({
        queryKey: ['comboData'],
        queryFn: async () => await client.GET("/user/api/combo/make", {
            params: {},
        }),
    })

    if (isPending || isFetching) return 'Loading...'

    if (error) return 'An error has occurred: ' + error.message

    return (
        <div>
            <Link to="/" className="back-link">← Back to Home</Link>
            <h2>Combo</h2>
            <table>
                <thead>
                <tr>
                    <th>Combo</th>
                </tr>
                </thead>
                <tbody key="movements">
                {data.data?.movements.map((m, i) =>
                    <tr key={i}>
                        <td key={i}>{m.description}</td>
                    </tr>
                )}
                </tbody>
            </table>
        </div>
    )
}

function Training() {
    const {isPending, error, data, isFetching} = useQuery({
        queryKey: ['trainingData'],
        queryFn: async () => await client.GET("/user/api/training", {
            params: {},
        }),
    })

    if (isPending || isFetching) return 'Loading...'

    if (error) return 'An error has occurred: ' + error.message

    return (
        <div>
            <Link to="/" className="back-link">← Back to Home</Link>
            <h2>Training</h2>
            <div className="p-4">
                <h3 className="text-xl font-bold mb-4">Duration: {data.data?.duration}</h3>
                <h3 className="text-xl font-bold mb-4">Sections:</h3>
                {data.data?.sections.map((section, i) => (
                    <div key={i} className="section mb-8 p-6 bg-gray-50 rounded-lg shadow-sm">
                        <div className="flex">
                            <div className="w-1/2">
                                <h4 className="text-lg font-semibold mb-2">{section.title} ({section.type})</h4>
                                <p className="text-gray-600 mb-1">Duration: {section.duration}</p>
                                <p className="text-gray-600 mb-3">Group: {section.group}</p>
                            </div>
                            <div className="w-1/2">
                                <ul>
                                    {section.exercises.map((exercise, j) => (
                                        <li key={j}>
                                            {exercise.kind === 'combo' ? (
                                                <div>
                                                    <div>Combo: {exercise.ref} - {exercise.duration}</div>
                                                    <ul>
                                                        {exercise.movements && exercise.movements.map((m, k) => (
                                                            <li key={k}>
                                                                {m.description}
                                                                {m.picture &&
                                                                    <img src={m.picture} alt={m.description}/>}
                                                                {m.video && <video src={m.video} controls/>}
                                                            </li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            ) : exercise.kind === 'composite' ? (
                                                <div>
                                                    <div>Composite: {exercise.ref} - {exercise.duration}</div>
                                                    <ul>
                                                        {exercise.exercises?.map((e, k) => (
                                                            <li key={k}>{e.kind}: {e.ref} - {e.duration}</li>
                                                        ))}
                                                    </ul>
                                                </div>
                                            ) : (
                                                <div>
                                                    Simple: {exercise.ref} - {exercise.duration}
                                                    {exercise.reps && ` (${exercise.reps} reps)`}
                                                </div>
                                            )}
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        </div>

                    </div>
                ))}
            </div>
        </div>
    )
}