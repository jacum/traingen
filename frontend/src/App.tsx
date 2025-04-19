import './App.css'
import './index.css'

import { BrowserRouter, Routes, Route, Link, Navigate } from 'react-router-dom'
import {useState} from 'react'
import { paths, components } from './services/user-api.ts'
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
    const [movementsCount, setMovementsCount] = useState<number>(6);
    const {isPending, error, data, isFetching} = useQuery({
        queryKey: ['comboData', movementsCount],
        queryFn: async () => await client.GET("/user/api/combo/make", {
            params: {
                query: {
                    movements: movementsCount
                }
            },
        }),
    })

    if (isPending || isFetching) return 'Loading...'

    if (error) return 'An error has occurred: ' + error.message

    return (
        <div className="p-4">
            <Link to="/" className="back-link mb-4 inline-block text-blue-500 hover:text-blue-700">← Back to Home</Link>
            <h2 className="text-2xl font-bold mb-4">Combo</h2>
            <div className="mb-4 flex gap-4 items-center">
                Movements: <input
                    type="number"
                    value={movementsCount}
                    onChange={(e) => setMovementsCount(Number(e.target.value))}
                    min="2" max="10"
                    className="border rounded px-2 py-1 w-20"
                />
            </div>
            <table className="w-full border-collapse border">
                <thead>
                <tr>
                    <th className="border p-2 bg-gray-100">Combo</th>
                </tr>
                </thead>
                <tbody key="movements">
                {data.data?.movements.map((m, i) =>
                    <tr key={i}>
                        <td key={i} className="border p-2">{m.description}</td>
                    </tr>
                )}
                </tbody>
            </table>
        </div>
    )
}

function Training() {
    const [totalMinutes, setTotalMinutes] = useState<number>(45);
    const [calisthenicsExercises, setCalisthenicsExercises] = useState<number>(5);
    const [warmupMinutes, setWarmupMinutes] = useState<number>(15);
    const [comboMovements, setComboMovements] = useState<number>(6);
    const [comboBuildup, setComboBuildup] = useState<number>(3);
    const [isDefault, setIsDefault] = useState<boolean>(true);

    const {isPending, error, data, isFetching, refetch} = useQuery({
        queryKey: ['trainingData'],
        enabled: isDefault,
        queryFn: async () => {
            const response = await client.GET("/user/api/training", {
                params: {
                    query: {
                        totalMinutes,
                        calisthenicsExercises,
                        warmupMinutes,
                        comboMovements,
                        comboBuildup
                    }
                },
            });
            setIsDefault(false);
            return response;
        },
    })

    const handleChange = (setter: (value: number) => void, value: number) => {
        setter(value);
        setIsDefault(false);
    };

    const handleRefetch = () => {
        refetch();
        setIsDefault(false);
    };

    if (isPending || isFetching) return 'Loading...'

    if (error) return 'An error has occurred: ' + error.message

    return (
        <div>
            <Link to="/" className="back-link">← Back to Home</Link>

            <div className="mb-4 flex gap-4 items-center">
                Total Minutes: <input
                type="number"
                value={totalMinutes}
                onChange={(e) => handleChange(setTotalMinutes, Number(e.target.value))}
                min="30" max="120"
                className="border rounded px-2 py-1 w-20"
            />
                Warmup Minutes: <input
                type="number"
                value={warmupMinutes}
                onChange={(e) => handleChange(setWarmupMinutes, Number(e.target.value))}
                min="5" max="30"
                className="border rounded px-2 py-1 w-20"
            /> Calisthenic exercises: <input
                type="number"
                value={calisthenicsExercises}
                onChange={(e) => handleChange(setCalisthenicsExercises, Number(e.target.value))}
                min="3" max="7"
                className="border rounded px-2 py-1 w-20"
            /> Combo movements: <input
                type="number"
                value={comboMovements}
                onChange={(e) => handleChange(setComboMovements, Number(e.target.value))}
                min="4" max="8"
                className="border rounded px-2 py-1 w-20"
            /> Combo buildup: <input
                type="number"
                value={comboBuildup}
                onChange={(e) => handleChange(setComboBuildup, Number(e.target.value))}
                min="2" max="4"
                className="border rounded px-2 py-1 w-20"
            />
                <button
                    onClick={handleRefetch}
                    disabled={isDefault}
                    className={`ml-4 px-4 py-2 text-white rounded transition-colors ${
                        isDefault
                            ? 'bg-gray-400 cursor-not-allowed'
                            : 'bg-blue-500 hover:bg-blue-600'
                    }`}>
                    Regenerate
                </button>
            </div>
            <div className="p-4">
                <h3 className="text-xl font-bold mb-4">{data.data?.duration}</h3>
                <div className="w-full h-12 bg-gray-100 rounded-lg mb-6 flex overflow-hidden">
                    {data.data?.sections.map((section, i) => {
                        const durationMatch = section.duration.match(/(\d+)/);
                        const seconds = durationMatch ? parseInt(durationMatch[1]) : 0;
                        const totalSeconds = data.data?.sections.reduce((acc, s) => {
                            const match = s.duration.match(/(\d+)/);
                            return acc + (match ? parseInt(match[1]) : 0);
                        }, 0) || 1;
                        const width = (seconds / totalSeconds) * 100;

                        const colors = {
                            'Warmup': 'bg-yellow-200',
                            'Calisthenics': 'bg-green-200',
                            'Workout': 'bg-blue-200',
                            'Combo': 'bg-red-200',
                            'Cooldown': 'bg-purple-200'
                        };

                        return (
                            <div
                                key={i}
                                className={`${colors[section.type as keyof typeof colors]} h-full`}
                                style={{width: `${width}%`}}

                            ><div className="text-xs text-black font-bold">{`${section.type}`}</div>
                                <div className="text-xs text-black font-bold">{`${section.duration}`}</div></div>
                        );
                    })}
                </div>

                {data.data?.sections.map((section, i) => (
                    <div key={i} className="section mb-8 p-6 bg-gray-50 rounded-lg shadow-sm">
                        <div className="flex">
                            <div className="w-1/2">
                                <h4 className="text-lg font-semibold mb-2">{section.type}</h4>
                                <p className="text-gray-600 mb-1">Duration: {section.duration}</p>
                                <p className="text-gray-600 mb-3">Group: {section.group}</p>
                            </div>
                            <div className="w-1/2">
                                <ul>
                                    {section.exercises.map((exercise, j) => (
                                        <li key={j} className="mb-4">
                                            {exercise.kind === 'combo' ? (
                                                <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                                                    <div className="font-semibold text-blue-700 mb-2">{exercise.ref} - {exercise.duration}</div>
                                                    <ul className="space-y-2">
                                                        {(exercise as components["schemas"]["ComboExercise"]).movements &&
                                                            (exercise as components["schemas"]["ComboExercise"]).movements.map(
                                                                (m, k) => (
                                                                    <li key={k} className="text-blue-600">
                                                                        {m.description}
                                                                        {m.picture &&
                                                                            <img src={m.picture} alt={m.description} className="mt-2 rounded"/>}
                                                                        {m.video &&
                                                                            <video src={m.video} controls className="mt-2 rounded"/>}
                                                                    </li>
                                                                ))}
                                                    </ul>
                                                </div>
                                            ) : exercise.kind === 'composite' ? (
                                                <div className="p-4 bg-green-50 rounded-lg border border-green-200">
                                                    <div className="font-semibold text-green-700 mb-2">{exercise.ref} - {exercise.duration}</div>
                                                    <ul className="space-y-2">
                                                        {(exercise as components["schemas"]["CompositeExercise"]).exercises?.map(
                                                            (e, k) => (
                                                                <li key={k} className="text-green-600">{e.kind}: {e.ref} - {e.duration}</li>
                                                            ))}
                                                    </ul>
                                                </div>
                                            ) : (
                                                <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                                                    <div className="font-semibold text-gray-700">
                                                        {exercise.ref} - {exercise.duration}
                                                        {exercise.reps && ` (${exercise.reps} reps)`}
                                                    </div>
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