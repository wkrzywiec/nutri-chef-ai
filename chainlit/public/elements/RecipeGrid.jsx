import { Card } from "@/components/ui/card"
import { ExternalLink } from "lucide-react"

function RecipeCard({ recipe }) {
  return (
    <Card className="overflow-hidden flex flex-row">
      {recipe.imageUrl && (
        <div className="w-[300px] h-[300px] shrink-0 overflow-hidden">
          <img
            src={recipe.imageUrl}
            alt={recipe.name}
            className="h-full w-full object-cover"
          />
        </div>
      )}
      <div className="flex flex-col justify-start p-3 gap-1 flex-1 min-w-0">
        <span className="text-base font-semibold leading-snug">
          {recipe.name || "Unknown recipe"}
        </span>
        {recipe.description && (
          <p className="text-xs text-muted-foreground line-clamp-3">
            {recipe.description}
          </p>
        )}
        {recipe.sourceUrl && (
          <a
            href={recipe.sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
          >
            <ExternalLink className="h-3 w-3" />
            {recipe.source || "Source"}
          </a>
        )}
      </div>
    </Card>
  )
}

export default function RecipeGrid() {
  const recipes = props.recipes || []

  return (
    <div className="flex flex-col gap-3 w-full">
      {recipes.map((recipe, i) => (
        <RecipeCard key={i} recipe={recipe} />
      ))}
    </div>
  )
}
