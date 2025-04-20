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
                <div className="bg-gray-100 rounded-lg p-6 text-center max-w-md mb-4">
                    <p className="text-xl font-bold mb-4">This is Training Generator! </p>
                    <p className="text-gray-700">
                        <br/>Choose between a quick <b>Combo</b> generator
                        or a complete <b>Training</b> plan with warmup, calisthenics, and bag work.
                    </p>
                </div>
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
    const {isPending, error, data, isFetching, refetch} = useQuery({
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
            <p className="font-bold mb-4">Adjust number of combo movements or just regenerate</p>
            <div className="mb-4 flex flex-wrap gap-4 items-center">
                <div className="flex items-center gap-2 w-full sm:w-auto">
                    <span>Movements:</span>
                    <input
                        type="range"
                        value={movementsCount}
                        onChange={(e) => setMovementsCount(Number(e.target.value))}
                        min="3" max="10"
                        className="w-40"
                    />
                    <span>{movementsCount}</span>
                </div>
                <button
                    onClick={() => refetch()}
                    className="w-full sm:w-auto px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
                >
                    Regenerate
                </button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full border-collapse border">
                    <tbody key="movements">
                    {data.data?.movements.map((m, i) =>
                        <tr key={i}>
                            <td key={i} className="border p-4 break-words">{m.description}</td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>
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

            <div className="mb-4 flex flex-col sm:flex-row gap-4">
                <div className="flex flex-col sm:flex-row flex-wrap gap-4">
                <div className="flex items-center gap-2">
                        <span>Total Minutes:</span>
                        <input
                            type="range"
                            value={totalMinutes}
                            onChange={(e) => handleChange(setTotalMinutes, Number(e.target.value))}
                            min="30" max="60" step="15"
                            className="w-40"
                        />
                        <span>{totalMinutes}</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span>Warmup Minutes:</span>
                        <input
                            type="range"
                            value={warmupMinutes}
                            onChange={(e) => handleChange(setWarmupMinutes, Number(e.target.value))}
                            min="5" max="30"
                            className="w-40"
                        />
                        <span>{warmupMinutes}</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span>Calisthenic series:</span>
                        <input
                            type="range"
                            value={calisthenicsExercises}
                            onChange={(e) => handleChange(setCalisthenicsExercises, Number(e.target.value))}
                            min="3" max="7"
                            className="w-40"
                        />
                        <span>{calisthenicsExercises}</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span>Combo movements:</span>
                        <input
                            type="range"
                            value={comboMovements}
                            onChange={(e) => handleChange(setComboMovements, Number(e.target.value))}
                            min="4" max="10"
                            className="w-40"
                        />
                        <span>{comboMovements}</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span>Combo buildup:</span>
                        <input
                            type="range"
                            value={comboBuildup}
                            onChange={(e) => handleChange(setComboBuildup, Number(e.target.value))}
                            min="2" max="4"
                            className="w-40"
                        />
                        <span>{comboBuildup}</span>
                    </div>
                </div>
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

                <div className="w-full bg-gray-100 rounded-lg mb-6 flex flex-col sm:flex-row overflow-hidden">
                {data.data?.sections.map((section, i) => {
                        const durationMatch = section.duration.match(/(\d+)/);
                        const seconds = durationMatch ? parseInt(durationMatch[1]) : 0;
                        const totalSeconds = data.data?.sections.reduce((acc, s) => {
                            const match = s.duration.match(/(\d+)/);
                            return acc + (match ? parseInt(match[1]) : 0);
                        }, 0) || 1;
                        const width = (seconds / totalSeconds) * 100;

                        const colors = {
                            'Warmup': 'bg-yellow-300',
                            'Calisthenics': 'bg-green-300',
                            'Workout': 'bg-blue-300',
                            'Combo': 'bg-red-300',
                            'Cooldown': 'bg-purple-300'
                        };

                        return (
                            <div
                                key={i}
                                className={`${colors[section.type as keyof typeof colors]} h-full py-4`}
                                style={{width: `${width}%`}}

                            ><div className="text-xs text-black font-bold">{`${section.type}`}</div>
                                <div className="text-xs text-black font-bold">{`${section.duration}`}</div></div>
                        );
                    })}
                </div>

                {data.data?.sections.map((section, i) => (
                    <div key={i} className="section mb-8 p-6 bg-gray-50 rounded-lg shadow-sm">
                        <div className="flex flex-col sm:flex-row">
                            <div className="w-full sm:w-1/2 mb-4 sm:mb-0">
                            <h4 className="text-lg font-semibold text-black mb-2">{section.type}</h4>
                                <p className="text-gray-600 mb-1">{section.duration}</p>
                                <p className="text-gray-600 mb-3">Group: {section.group}</p>
                            </div>
                            <div className="w-full sm:w-1/2">
                                <ul className="space-y-4">
                                {section.exercises.map((exercise, j) => (
                                        <li key={j} className="mb-4">
                                            {exercise.kind === 'combo' ? (
                                                <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                                                    <div className="font-semibold text-blue-700 mb-2">{exercise.title} - {exercise.duration}</div>
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
                                                    <div className="font-semibold text-green-700 mb-2">{exercise.title} - {exercise.duration}</div>
                                                    <ul className="space-y-2">
                                                        {(exercise as components["schemas"]["CompositeExercise"]).exercises?.map(
                                                            (e, k) => (
                                                                <li key={k} className="text-green-600">{e.kind}: {e.title} - {e.duration}</li>
                                                            ))}
                                                    </ul>
                                                </div>
                                            ) : (
                                                <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                                                    <div className="font-semibold text-gray-700">
                                                        {exercise.title} - {exercise.duration}
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